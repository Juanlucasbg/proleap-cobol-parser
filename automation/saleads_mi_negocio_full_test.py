#!/usr/bin/env python3
"""
SaleADS Mi Negocio full workflow validation.

This script is environment-agnostic:
- It does not hardcode any SaleADS domain.
- It expects SALEADS_LOGIN_URL to point to the current environment login page.
- It relies on visible text selectors whenever possible.
"""

from __future__ import annotations

import json
import os
import re
import traceback
from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable, Optional

from playwright.sync_api import BrowserContext, Error, Page, TimeoutError, sync_playwright


DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com"
DEFAULT_REPORT_NAME = "saleads_mi_negocio_full_test_report"


@dataclass
class StepResult:
    name: str
    status: str = "FAIL"
    details: list[str] = field(default_factory=list)
    screenshots: list[str] = field(default_factory=list)
    final_url: Optional[str] = None


def now_utc() -> str:
    return datetime.now(UTC).isoformat()


def ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")


def append_detail(step: StepResult, message: str) -> None:
    step.details.append(message)


def safe_screenshot(page: Page, output_dir: Path, filename: str, full_page: bool = False) -> Optional[str]:
    file_path = output_dir / filename
    try:
        page.screenshot(path=str(file_path), full_page=full_page)
        return str(file_path)
    except Exception as exc:  # pylint: disable=broad-except
        return f"[screenshot-error] {exc}"


def wait_for_ui(page: Page, timeout_ms: int = 15000) -> None:
    page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
    try:
        page.wait_for_load_state("networkidle", timeout=timeout_ms)
    except TimeoutError:
        # Some pages keep active requests alive; DOM is usually enough.
        pass


def click_visible_text(page: Page, text: str, exact: bool = False, timeout_ms: int = 10000) -> bool:
    locator = page.get_by_text(text, exact=exact).first
    try:
        locator.wait_for(state="visible", timeout=timeout_ms)
        locator.click()
        wait_for_ui(page)
        return True
    except TimeoutError:
        return False
    except Error:
        return False


def visible_text_present(page: Page, pattern: str, timeout_ms: int = 10000) -> bool:
    locator = page.get_by_text(re.compile(pattern, re.IGNORECASE)).first
    try:
        locator.wait_for(state="visible", timeout=timeout_ms)
        return True
    except TimeoutError:
        return False


def mark_prerequisite_failure(step: StepResult, prereq_name: str) -> None:
    step.status = "FAIL"
    append_detail(step, f"Prerequisite failed: {prereq_name}.")


def detect_google_selector_and_choose_email(page: Page, target_email: str, step: StepResult) -> bool:
    """
    Handles either:
    - Google account chooser page where email item is clickable.
    - Google identifier page where email must be typed.
    """
    try:
        wait_for_ui(page)
    except Exception:  # pylint: disable=broad-except
        pass

    account_choice = page.get_by_text(target_email, exact=True).first
    if account_choice.count() > 0:
        try:
            account_choice.click()
            wait_for_ui(page)
            append_detail(step, f"Selected Google account: {target_email}")
            return True
        except Exception as exc:  # pylint: disable=broad-except
            append_detail(step, f"Unable to click account chooser item ({target_email}): {exc}")

    email_input_candidates = [
        page.locator('input[type="email"]').first,
        page.locator('input[name="identifier"]').first,
        page.get_by_label(re.compile(r"email|correo", re.IGNORECASE)).first,
    ]
    for email_input in email_input_candidates:
        try:
            if email_input.count() > 0:
                email_input.click()
                email_input.fill(target_email)
                append_detail(step, f"Entered Google email: {target_email}")
                if click_visible_text(page, "Next", exact=True) or click_visible_text(page, "Siguiente", exact=True):
                    append_detail(step, "Clicked next after entering Google email.")
                    return True
                try:
                    page.keyboard.press("Enter")
                    wait_for_ui(page)
                    append_detail(step, "Submitted Google email with Enter key.")
                    return True
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step, f"Could not submit Google email with keyboard Enter: {exc}")
        except Exception:  # pylint: disable=broad-except
            continue

    append_detail(step, "Google account chooser or email input was not detected.")
    return False


def attempt_google_password_if_available(page: Page, step: StepResult) -> bool:
    password = os.getenv("GOOGLE_ACCOUNT_PASSWORD", "").strip()
    password_input = page.locator('input[type="password"]').first

    try:
        if password_input.count() == 0:
            append_detail(step, "Google password input not detected at this moment.")
            return False
    except Exception:  # pylint: disable=broad-except
        return False

    if not password:
        append_detail(
            step,
            "Google password screen detected, but GOOGLE_ACCOUNT_PASSWORD env var is not set.",
        )
        return False

    try:
        password_input.click()
        password_input.fill(password)
        append_detail(step, "Entered Google password from GOOGLE_ACCOUNT_PASSWORD.")
        if click_visible_text(page, "Next", exact=True) or click_visible_text(page, "Siguiente", exact=True):
            append_detail(step, "Clicked next after entering Google password.")
            return True
        page.keyboard.press("Enter")
        wait_for_ui(page)
        append_detail(step, "Submitted Google password with Enter key.")
        return True
    except Exception as exc:  # pylint: disable=broad-except
        append_detail(step, f"Failed while trying to submit Google password: {exc}")
        return False


def validate_legal_link(
    context: BrowserContext,
    app_page: Page,
    link_text_pattern: str,
    heading_pattern: str,
    screenshot_name: str,
    step: StepResult,
    output_dir: Path,
) -> None:
    link = app_page.get_by_text(re.compile(link_text_pattern, re.IGNORECASE)).first
    link.wait_for(state="visible", timeout=10000)

    popup_page: Optional[Page] = None
    current_url = app_page.url
    try:
        with context.expect_page(timeout=5000) as popup_info:
            link.click()
        popup_page = popup_info.value
        popup_page.wait_for_load_state("domcontentloaded")
        target_page = popup_page
        append_detail(step, "Legal page opened in a new tab.")
    except TimeoutError:
        link.click()
        wait_for_ui(app_page)
        target_page = app_page
        append_detail(step, "Legal page opened in the same tab.")

    heading = target_page.get_by_text(re.compile(heading_pattern, re.IGNORECASE)).first
    heading.wait_for(state="visible", timeout=15000)
    append_detail(step, f"Validated heading pattern: {heading_pattern}")

    legal_content = target_page.locator("main, article, body").first
    legal_content.wait_for(state="visible", timeout=15000)
    append_detail(step, "Legal content is visible.")

    shot = safe_screenshot(target_page, output_dir, screenshot_name, full_page=True)
    if shot:
        step.screenshots.append(shot)

    step.final_url = target_page.url

    if popup_page:
        popup_page.close()
    else:
        if target_page.url != current_url:
            target_page.go_back()
            wait_for_ui(target_page)

    step.status = "PASS"


def run() -> int:
    script_dir = Path(__file__).resolve().parent
    artifacts_root = Path(os.getenv("SALEADS_ARTIFACTS_DIR", str(script_dir / "artifacts"))).resolve()
    screenshots_dir = ensure_dir(artifacts_root / "screenshots")
    report_dir = ensure_dir(artifacts_root / "reports")

    login_url = os.getenv("SALEADS_LOGIN_URL", "").strip()
    google_email = os.getenv("GOOGLE_ACCOUNT_EMAIL", DEFAULT_GOOGLE_EMAIL).strip()
    headless = os.getenv("PW_HEADLESS", "true").strip().lower() not in {"0", "false", "no"}

    steps: dict[str, StepResult] = {
        "Login": StepResult("Login"),
        "Mi Negocio menu": StepResult("Mi Negocio menu"),
        "Agregar Negocio modal": StepResult("Agregar Negocio modal"),
        "Administrar Negocios view": StepResult("Administrar Negocios view"),
        "Informacion General": StepResult("Informacion General"),
        "Detalles de la Cuenta": StepResult("Detalles de la Cuenta"),
        "Tus Negocios": StepResult("Tus Negocios"),
        "Terminos y Condiciones": StepResult("Terminos y Condiciones"),
        "Politica de Privacidad": StepResult("Politica de Privacidad"),
    }

    run_summary = {
        "test_name": "saleads_mi_negocio_full_test",
        "goal": "Login to SaleADS.ai with Google and validate Mi Negocio workflow.",
        "started_at_utc": now_utc(),
        "environment": {
            "saleads_login_url": login_url or None,
            "google_account_email": google_email,
            "headless": headless,
        },
    }

    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=headless)
            context = browser.new_context(viewport={"width": 1600, "height": 1200})
            page = context.new_page()

            # Step 1 - Login with Google
            step1 = steps["Login"]
            if not login_url:
                append_detail(
                    step1,
                    "SALEADS_LOGIN_URL is required to run in an environment-agnostic way.",
                )
                append_detail(
                    step1,
                    "Set SALEADS_LOGIN_URL to the current environment login page before running.",
                )
            else:
                try:
                    page.goto(login_url, wait_until="domcontentloaded", timeout=30000)
                    wait_for_ui(page)
                    append_detail(step1, f"Navigated to login page: {login_url}")

                    login_clicked = (
                        click_visible_text(page, "Sign in with Google")
                        or click_visible_text(page, "Iniciar sesi\u00f3n con Google")
                        or click_visible_text(page, "Inicia sesi\u00f3n con Google")
                        or click_visible_text(page, "Sign in")
                        or click_visible_text(page, "Inicia sesi\u00f3n")
                        or click_visible_text(page, "Iniciar sesi\u00f3n")
                        or click_visible_text(page, "Google")
                    )
                    if not login_clicked:
                        raise AssertionError("Could not locate a login button by visible text.")

                    append_detail(step1, "Clicked login entry point.")
                    detect_google_selector_and_choose_email(page, google_email, step1)
                    attempt_google_password_if_available(page, step1)
                    wait_for_ui(page)

                    main_interface_ok = (
                        visible_text_present(page, r"Negocio", timeout_ms=15000)
                        or visible_text_present(page, r"Mi\s+Negocio", timeout_ms=15000)
                        or page.locator("nav, aside").first.count() > 0
                    )
                    if main_interface_ok:
                        sidebar_visible = page.locator("aside, nav").first.count() > 0
                        if sidebar_visible:
                            append_detail(step1, "Main application interface and left navigation detected.")
                        else:
                            append_detail(step1, "Main app indicators detected, but sidebar selector is not explicit.")
                        shot = safe_screenshot(page, screenshots_dir, "step1_dashboard_loaded.png", full_page=True)
                        if shot:
                            step1.screenshots.append(shot)
                        step1.status = "PASS"
                    else:
                        append_detail(
                            step1,
                            "Main interface was not detected after login flow. Authentication might still require manual action.",
                        )
                        shot = safe_screenshot(page, screenshots_dir, "step1_login_blocked.png", full_page=True)
                        if shot:
                            step1.screenshots.append(shot)
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step1, f"Login flow failed with error: {exc}")
                    shot = safe_screenshot(page, screenshots_dir, "step1_login_error.png", full_page=True)
                    if shot:
                        step1.screenshots.append(shot)

            # Step 2 - Open Mi Negocio menu
            step2 = steps["Mi Negocio menu"]
            if step1.status != "PASS":
                mark_prerequisite_failure(step2, "Login")
            else:
                try:
                    mi_negocio_clicked = click_visible_text(page, "Mi Negocio", exact=False)
                    if not mi_negocio_clicked:
                        negocio_clicked = click_visible_text(page, "Negocio", exact=False)
                        if not negocio_clicked:
                            raise AssertionError("Could not click Negocio / Mi Negocio.")
                        mi_negocio_clicked = click_visible_text(page, "Mi Negocio", exact=False)
                        if not mi_negocio_clicked:
                            raise AssertionError("Could not click Mi Negocio option after opening Negocio.")

                    agregar_visible = visible_text_present(page, r"Agregar\s+Negocio")
                    administrar_visible = visible_text_present(page, r"Administrar\s+Negocios")
                    if not (agregar_visible and administrar_visible):
                        raise AssertionError("Expected submenu options are not visible.")

                    shot = safe_screenshot(page, screenshots_dir, "step2_mi_negocio_expanded.png", full_page=True)
                    if shot:
                        step2.screenshots.append(shot)
                    step2.status = "PASS"
                    append_detail(step2, "Mi Negocio submenu expanded with expected options.")
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step2, f"Mi Negocio menu validation failed: {exc}")

            # Step 3 - Validate Agregar Negocio modal
            step3 = steps["Agregar Negocio modal"]
            if step2.status != "PASS":
                mark_prerequisite_failure(step3, "Mi Negocio menu")
            else:
                try:
                    if not click_visible_text(page, "Agregar Negocio", exact=False):
                        raise AssertionError("Could not click Agregar Negocio.")

                    required_patterns = [
                        r"Crear\s+Nuevo\s+Negocio",
                        r"Nombre\s+del\s+Negocio",
                        r"Tienes\s+2\s+de\s+3\s+negocios",
                        r"Cancelar",
                        r"Crear\s+Negocio",
                    ]
                    for pattern in required_patterns:
                        if not visible_text_present(page, pattern, timeout_ms=12000):
                            raise AssertionError(f"Missing expected modal content: {pattern}")

                    name_input = page.get_by_label(re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)).first
                    if name_input.count() == 0:
                        name_input = page.locator('input[placeholder*="Negocio"], input[name*="negocio"]').first
                    if name_input.count() > 0:
                        name_input.click()
                        name_input.fill("Negocio Prueba Automatizacion")
                        append_detail(step3, "Typed sample business name in Nombre del Negocio.")

                    shot = safe_screenshot(page, screenshots_dir, "step3_agregar_negocio_modal.png", full_page=True)
                    if shot:
                        step3.screenshots.append(shot)

                    click_visible_text(page, "Cancelar", exact=True)
                    step3.status = "PASS"
                    append_detail(step3, "Agregar Negocio modal validated and closed via Cancelar.")
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step3, f"Agregar Negocio modal validation failed: {exc}")

            # Step 4 - Open Administrar Negocios
            step4 = steps["Administrar Negocios view"]
            if step2.status != "PASS":
                mark_prerequisite_failure(step4, "Mi Negocio menu")
            else:
                try:
                    if not visible_text_present(page, r"Administrar\s+Negocios", timeout_ms=5000):
                        click_visible_text(page, "Mi Negocio", exact=False)
                    if not click_visible_text(page, "Administrar Negocios", exact=False):
                        raise AssertionError("Could not click Administrar Negocios.")

                    section_patterns = [
                        r"Informaci[o\u00f3]n\s+General",
                        r"Detalles\s+de\s+la\s+Cuenta",
                        r"Tus\s+Negocios",
                        r"Secci[o\u00f3]n\s+Legal",
                    ]
                    for pattern in section_patterns:
                        if not visible_text_present(page, pattern, timeout_ms=15000):
                            raise AssertionError(f"Missing section: {pattern}")

                    shot = safe_screenshot(page, screenshots_dir, "step4_administrar_negocios_page.png", full_page=True)
                    if shot:
                        step4.screenshots.append(shot)
                    step4.status = "PASS"
                    append_detail(step4, "Administrar Negocios page and key sections are visible.")
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step4, f"Administrar Negocios validation failed: {exc}")

            # Step 5 - Validate Informacion General
            step5 = steps["Informacion General"]
            if step4.status != "PASS":
                mark_prerequisite_failure(step5, "Administrar Negocios view")
            else:
                try:
                    checks = [
                        (r"BUSINESS\s+PLAN", "Business plan badge"),
                        (r"Cambiar\s+Plan", "Cambiar Plan button"),
                    ]
                    for pattern, label in checks:
                        if not visible_text_present(page, pattern, timeout_ms=12000):
                            raise AssertionError(f"{label} not visible.")

                    email_visible = visible_text_present(page, r"[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}", timeout_ms=12000)
                    name_hint_visible = (
                        page.locator("h1, h2, [data-testid*='name'], [class*='name']").first.count() > 0
                    )
                    if not email_visible:
                        raise AssertionError("User email is not visible.")
                    if not name_hint_visible:
                        append_detail(step5, "Could not strongly identify user name element; continuing with visible account context.")
                    else:
                        append_detail(step5, "User name area appears visible.")

                    step5.status = "PASS"
                    append_detail(step5, "Informacion General section validated.")
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step5, f"Informacion General validation failed: {exc}")

            # Step 6 - Validate Detalles de la Cuenta
            step6 = steps["Detalles de la Cuenta"]
            if step4.status != "PASS":
                mark_prerequisite_failure(step6, "Administrar Negocios view")
            else:
                try:
                    required = [
                        r"Cuenta\s+creada",
                        r"Estado\s+activo",
                        r"Idioma\s+seleccionado",
                    ]
                    for pattern in required:
                        if not visible_text_present(page, pattern, timeout_ms=12000):
                            raise AssertionError(f"Missing account detail: {pattern}")
                    step6.status = "PASS"
                    append_detail(step6, "Detalles de la Cuenta section validated.")
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step6, f"Detalles de la Cuenta validation failed: {exc}")

            # Step 7 - Validate Tus Negocios
            step7 = steps["Tus Negocios"]
            if step4.status != "PASS":
                mark_prerequisite_failure(step7, "Administrar Negocios view")
            else:
                try:
                    required = [
                        r"Tus\s+Negocios",
                        r"Agregar\s+Negocio",
                        r"Tienes\s+2\s+de\s+3\s+negocios",
                    ]
                    for pattern in required:
                        if not visible_text_present(page, pattern, timeout_ms=12000):
                            raise AssertionError(f"Missing business section content: {pattern}")
                    # Generic container assertion to support varied markup.
                    if page.locator("ul, table, [role='list'], [class*='business']").first.count() == 0:
                        append_detail(step7, "Business list container was not strongly identified; text evidence was found.")
                    step7.status = "PASS"
                    append_detail(step7, "Tus Negocios section validated.")
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step7, f"Tus Negocios validation failed: {exc}")

            # Step 8 - Validate Terminos y Condiciones
            step8 = steps["Terminos y Condiciones"]
            if step4.status != "PASS":
                mark_prerequisite_failure(step8, "Administrar Negocios view")
            else:
                try:
                    validate_legal_link(
                        context=context,
                        app_page=page,
                        link_text_pattern=r"T[e\u00e9]rminos\s+y\s+Condiciones",
                        heading_pattern=r"T[e\u00e9]rminos\s+y\s+Condiciones",
                        screenshot_name="step8_terminos_condiciones.png",
                        step=step8,
                        output_dir=screenshots_dir,
                    )
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step8, f"Terminos y Condiciones validation failed: {exc}")

            # Step 9 - Validate Politica de Privacidad
            step9 = steps["Politica de Privacidad"]
            if step4.status != "PASS":
                mark_prerequisite_failure(step9, "Administrar Negocios view")
            else:
                try:
                    validate_legal_link(
                        context=context,
                        app_page=page,
                        link_text_pattern=r"Pol[i\u00ed]tica\s+de\s+Privacidad",
                        heading_pattern=r"Pol[i\u00ed]tica\s+de\s+Privacidad",
                        screenshot_name="step9_politica_privacidad.png",
                        step=step9,
                        output_dir=screenshots_dir,
                    )
                except Exception as exc:  # pylint: disable=broad-except
                    append_detail(step9, f"Politica de Privacidad validation failed: {exc}")

            context.close()
            browser.close()
    except Exception as exc:  # pylint: disable=broad-except
        # If Playwright cannot start at all, preserve actionable diagnostics.
        for step in steps.values():
            append_detail(step, f"Global execution error before step completion: {exc}")
        run_summary["fatal_error"] = traceback.format_exc()

    ended_at = now_utc()
    run_summary["ended_at_utc"] = ended_at
    run_summary["results"] = {name: asdict(step) for name, step in steps.items()}
    run_summary["overall_status"] = "PASS" if all(step.status == "PASS" for step in steps.values()) else "FAIL"

    json_report = report_dir / f"{DEFAULT_REPORT_NAME}.json"
    write_json(json_report, run_summary)

    markdown_lines = [
        "# SaleADS Mi Negocio Full Test Report",
        "",
        f"- Started (UTC): {run_summary['started_at_utc']}",
        f"- Ended (UTC): {run_summary['ended_at_utc']}",
        f"- Overall status: **{run_summary['overall_status']}**",
        "",
        "## Final Report (PASS/FAIL)",
        "",
    ]
    for key, step in steps.items():
        markdown_lines.append(f"- {key}: **{step.status}**")
    markdown_lines.append("")

    for key, step in steps.items():
        markdown_lines.append(f"## {key}")
        markdown_lines.extend([f"- Status: **{step.status}**"])
        if step.final_url:
            markdown_lines.append(f"- Final URL: `{step.final_url}`")
        if step.screenshots:
            markdown_lines.append("- Screenshots:")
            markdown_lines.extend([f"  - `{s}`" for s in step.screenshots])
        if step.details:
            markdown_lines.append("- Details:")
            markdown_lines.extend([f"  - {d}" for d in step.details])
        markdown_lines.append("")

    md_report = report_dir / f"{DEFAULT_REPORT_NAME}.md"
    md_report.write_text("\n".join(markdown_lines), encoding="utf-8")

    print(json.dumps({"overall_status": run_summary["overall_status"], "report_json": str(json_report), "report_md": str(md_report)}))
    return 0 if run_summary["overall_status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(run())
