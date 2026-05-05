#!/usr/bin/env python3
"""
End-to-end SaleADS Mi Negocio workflow validation.

Usage examples:
  python automation/saleads_mi_negocio_full_test.py --login-url "https://.../login"
  SALEADS_LOGIN_URL="https://.../login" python automation/saleads_mi_negocio_full_test.py
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

from playwright.sync_api import Page, TimeoutError as PlaywrightTimeoutError, sync_playwright


TEST_NAME = "saleads_mi_negocio_full_test"
DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
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
    screenshots: List[str] = field(default_factory=list)
    urls: List[str] = field(default_factory=list)

    def pass_with(self, detail: str) -> None:
        self.status = "PASS"
        self.details.append(detail)

    def fail_with(self, detail: str) -> None:
        self.status = "FAIL"
        self.details.append(detail)


def now_utc_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def sanitize(name: str) -> str:
    normalized = re.sub(r"[^\w\-]+", "_", name.strip(), flags=re.UNICODE)
    normalized = re.sub(r"_+", "_", normalized)
    return normalized.strip("_").lower() or "checkpoint"


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def safe_wait_ui(page: Page, timeout_ms: int = 20_000) -> None:
    try:
        page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
    except PlaywrightTimeoutError:
        pass
    try:
        page.wait_for_load_state("networkidle", timeout=timeout_ms)
    except PlaywrightTimeoutError:
        pass
    page.wait_for_timeout(500)


def capture_screenshot(page: Page, output_dir: Path, label: str, full_page: bool = False) -> str:
    filename = f"{sanitize(label)}.png"
    path = output_dir / filename
    page.screenshot(path=str(path), full_page=full_page)
    return str(path)


def pattern_for_text(text: str) -> re.Pattern[str]:
    return re.compile(re.escape(text), re.IGNORECASE)


def try_click_locator(page: Page, locator, timeout_ms: int = 2_000) -> bool:
    try:
        locator.first.wait_for(state="visible", timeout=timeout_ms)
        locator.first.click(timeout=timeout_ms)
        safe_wait_ui(page)
        return True
    except Exception:
        return False


def click_by_visible_text(page: Page, texts: Sequence[str], timeout_ms: int = 2_000) -> Tuple[bool, Optional[str]]:
    for text in texts:
        exact = re.compile(rf"^{re.escape(text)}$", re.IGNORECASE)
        fuzzy = pattern_for_text(text)
        locators = [
            page.get_by_role("button", name=exact),
            page.get_by_role("button", name=fuzzy),
            page.get_by_role("link", name=exact),
            page.get_by_role("link", name=fuzzy),
            page.get_by_role("menuitem", name=exact),
            page.get_by_role("menuitem", name=fuzzy),
            page.get_by_text(exact),
            page.get_by_text(fuzzy),
        ]
        for locator in locators:
            if try_click_locator(page, locator, timeout_ms=timeout_ms):
                return True, text
    return False, None


def is_text_visible(page: Page, text: str, timeout_ms: int = 6_000) -> bool:
    patterns = [re.compile(rf"^{re.escape(text)}$", re.IGNORECASE), pattern_for_text(text)]
    locators = []
    for pattern in patterns:
        locators.extend(
            [
                page.get_by_text(pattern),
                page.get_by_role("heading", name=pattern),
                page.get_by_role("button", name=pattern),
                page.get_by_role("link", name=pattern),
            ]
        )
    for locator in locators:
        try:
            locator.first.wait_for(state="visible", timeout=timeout_ms)
            return True
        except Exception:
            continue
    return False


def append_prereq_failures(step_results: Dict[str, StepResult], failed_after: str, reason: str) -> None:
    start_marking = False
    for field in REPORT_FIELDS:
        if field == failed_after:
            start_marking = True
            continue
        if start_marking:
            step_results[field].fail_with(f"Prerequisite failed before this step: {reason}")


def get_login_url(cli_value: Optional[str]) -> Optional[str]:
    if cli_value:
        return cli_value
    env_keys = [
        "SALEADS_LOGIN_URL",
        "SALEADS_URL",
        "SALEADS_BASE_URL",
        "APP_URL",
        "BASE_URL",
    ]
    for key in env_keys:
        value = os.getenv(key, "").strip()
        if value:
            return value
    return None


def login_with_google(
    page: Page,
    output_dir: Path,
    result: StepResult,
    login_url: Optional[str],
    account_email: str,
) -> bool:
    if page.url == "about:blank":
        if not login_url:
            result.fail_with(
                "No login URL configured and browser started on about:blank. "
                "Set SALEADS_LOGIN_URL or pass --login-url."
            )
            return False
        page.goto(login_url, wait_until="domcontentloaded")
        safe_wait_ui(page)
        result.details.append(f"Opened login URL: {page.url}")
    else:
        safe_wait_ui(page)
        result.details.append(f"Using preloaded page URL: {page.url}")

    login_texts = [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Ingresar con Google",
        "Login with Google",
        "Acceder con Google",
    ]
    clicked, chosen_text = click_by_visible_text(page, login_texts)
    if not clicked:
        result.fail_with("Could not find a visible Google login button.")
        return False
    result.details.append(f"Clicked Google login control via text: {chosen_text}")

    # If Google account picker appears, choose the requested account.
    safe_wait_ui(page)
    account_clicked, _ = click_by_visible_text(page, [account_email], timeout_ms=4_000)
    if account_clicked:
        result.details.append(f"Selected Google account: {account_email}")
        safe_wait_ui(page)
    else:
        result.details.append("Google account chooser not shown or target account not listed.")

    sidebar_visible = (
        is_text_visible(page, "Negocio")
        or is_text_visible(page, "Mi Negocio")
        or is_text_visible(page, "Agregar Negocio")
    )
    if not sidebar_visible:
        result.fail_with("Main application interface/sidebar was not detected after login.")
        return False

    screenshot = capture_screenshot(page, output_dir, "01_dashboard_loaded")
    result.screenshots.append(screenshot)
    result.pass_with("Login succeeded and left sidebar navigation is visible.")
    return True


def validate_mi_negocio_menu(page: Page, output_dir: Path, result: StepResult) -> bool:
    clicked_negocio, _ = click_by_visible_text(page, ["Negocio"])
    if not clicked_negocio:
        result.fail_with("Could not click sidebar section 'Negocio'.")
        return False

    clicked_mi_negocio, _ = click_by_visible_text(page, ["Mi Negocio"])
    if not clicked_mi_negocio:
        result.fail_with("Could not click menu option 'Mi Negocio'.")
        return False

    visible_agregar = is_text_visible(page, "Agregar Negocio")
    visible_admin = is_text_visible(page, "Administrar Negocios")
    if not (visible_agregar and visible_admin):
        result.fail_with(
            "Submenu did not show both expected options: 'Agregar Negocio' and 'Administrar Negocios'."
        )
        return False

    screenshot = capture_screenshot(page, output_dir, "02_mi_negocio_menu_expanded")
    result.screenshots.append(screenshot)
    result.pass_with("Mi Negocio submenu expanded with required options.")
    return True


def validate_agregar_negocio_modal(page: Page, output_dir: Path, result: StepResult) -> bool:
    clicked, _ = click_by_visible_text(page, ["Agregar Negocio"])
    if not clicked:
        result.fail_with("Could not click 'Agregar Negocio'.")
        return False

    title_ok = is_text_visible(page, "Crear Nuevo Negocio")
    quota_ok = is_text_visible(page, "Tienes 2 de 3 negocios")
    cancel_ok = is_text_visible(page, "Cancelar")
    create_ok = is_text_visible(page, "Crear Negocio")

    input_exists = False
    try:
        input_label = page.get_by_label(pattern_for_text("Nombre del Negocio"))
        input_label.first.wait_for(state="visible", timeout=5_000)
        input_exists = True
    except Exception:
        try:
            input_placeholder = page.get_by_placeholder(pattern_for_text("Nombre del Negocio"))
            input_placeholder.first.wait_for(state="visible", timeout=5_000)
            input_exists = True
        except Exception:
            input_exists = False

    if not all([title_ok, input_exists, quota_ok, cancel_ok, create_ok]):
        result.fail_with(
            "Agregar Negocio modal is missing one or more required controls/texts "
            "(title/input/quota/buttons)."
        )
        return False

    # Optional interaction requested in the workflow.
    try:
        editable = page.get_by_label(pattern_for_text("Nombre del Negocio")).first
        editable.click(timeout=2_000)
        editable.fill("Negocio Prueba Automatización", timeout=3_000)
    except Exception:
        try:
            editable = page.get_by_placeholder(pattern_for_text("Nombre del Negocio")).first
            editable.click(timeout=2_000)
            editable.fill("Negocio Prueba Automatización", timeout=3_000)
        except Exception:
            pass

    screenshot = capture_screenshot(page, output_dir, "03_agregar_negocio_modal")
    result.screenshots.append(screenshot)

    click_by_visible_text(page, ["Cancelar"])
    result.pass_with("Agregar Negocio modal validated and closed with Cancelar.")
    return True


def validate_administrar_negocios_view(page: Page, output_dir: Path, result: StepResult) -> bool:
    # Re-expand menu if needed.
    if not is_text_visible(page, "Administrar Negocios", timeout_ms=2_000):
        click_by_visible_text(page, ["Negocio"])
        click_by_visible_text(page, ["Mi Negocio"])

    clicked, _ = click_by_visible_text(page, ["Administrar Negocios"])
    if not clicked:
        result.fail_with("Could not click 'Administrar Negocios'.")
        return False

    required_sections = [
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Sección Legal",
    ]
    missing = [section for section in required_sections if not is_text_visible(page, section)]
    if missing:
        result.fail_with(f"Missing required sections on account page: {', '.join(missing)}")
        return False

    screenshot = capture_screenshot(page, output_dir, "04_administrar_negocios_page_full", full_page=True)
    result.screenshots.append(screenshot)
    result.pass_with("Administrar Negocios page loaded with all required sections.")
    return True


def validate_informacion_general(page: Page, result: StepResult) -> bool:
    body_text = page.locator("body").inner_text(timeout=5_000)
    email_match = re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", body_text)
    name_hint = (
        is_text_visible(page, "Nombre")
        or is_text_visible(page, "Usuario")
        or is_text_visible(page, "Perfil")
    )
    plan_ok = is_text_visible(page, "BUSINESS PLAN")
    change_plan_ok = is_text_visible(page, "Cambiar Plan")

    if not email_match:
        result.fail_with("No visible email detected in Información General.")
        return False
    if not name_hint:
        result.fail_with("No visible user-name indicator found in Información General.")
        return False
    if not plan_ok or not change_plan_ok:
        result.fail_with("BUSINESS PLAN or Cambiar Plan is not visible in Información General.")
        return False

    result.pass_with("Información General shows user details, plan, and plan-change control.")
    return True


def validate_detalles_cuenta(page: Page, result: StepResult) -> bool:
    required = ["Cuenta creada", "Estado activo", "Idioma seleccionado"]
    missing = [item for item in required if not is_text_visible(page, item)]
    if missing:
        result.fail_with(f"Missing Detalles de la Cuenta labels: {', '.join(missing)}")
        return False
    result.pass_with("Detalles de la Cuenta contains all required labels.")
    return True


def validate_tus_negocios(page: Page, result: StepResult) -> bool:
    section_ok = is_text_visible(page, "Tus Negocios")
    add_ok = is_text_visible(page, "Agregar Negocio")
    quota_ok = is_text_visible(page, "Tienes 2 de 3 negocios")

    # Heuristic: there should be business-related card/list content under this view.
    business_text_count = len(re.findall(r"negocio", page.locator("body").inner_text(timeout=5_000), flags=re.I))
    list_ok = business_text_count >= 2

    if not all([section_ok, add_ok, quota_ok, list_ok]):
        result.fail_with(
            "Tus Negocios validation failed (section/list/button/quota visibility did not match expectations)."
        )
        return False
    result.pass_with("Tus Negocios section, controls, and quota text are visible.")
    return True


def open_legal_and_validate(
    app_page: Page,
    output_dir: Path,
    trigger_text: str,
    expected_heading: str,
    screenshot_label: str,
    result: StepResult,
) -> bool:
    safe_wait_ui(app_page)
    popup_page = None
    current_url = app_page.url

    try:
        with app_page.context.expect_page(timeout=6_000) as popup_info:
            clicked, _ = click_by_visible_text(app_page, [trigger_text])
            if not clicked:
                result.fail_with(f"Could not click legal link: {trigger_text}")
                return False
        popup_page = popup_info.value
        popup_page.wait_for_load_state("domcontentloaded", timeout=20_000)
        safe_wait_ui(popup_page)
        target_page = popup_page
        result.details.append(f"Opened in new tab for '{trigger_text}'.")
    except PlaywrightTimeoutError:
        clicked, _ = click_by_visible_text(app_page, [trigger_text])
        if not clicked:
            result.fail_with(f"Could not click legal link: {trigger_text}")
            return False
        safe_wait_ui(app_page)
        target_page = app_page
        result.details.append(f"Opened in same tab for '{trigger_text}'.")

    heading_ok = is_text_visible(target_page, expected_heading)
    body_text = target_page.locator("body").inner_text(timeout=8_000)
    content_ok = len(body_text.strip()) > 120

    if not heading_ok or not content_ok:
        result.fail_with(
            f"Legal page validation failed for '{trigger_text}' "
            f"(heading/content visibility did not match expectations)."
        )
        return False

    screenshot = capture_screenshot(target_page, output_dir, screenshot_label, full_page=True)
    result.screenshots.append(screenshot)
    result.urls.append(target_page.url)
    result.pass_with(f"Validated legal page '{expected_heading}'.")

    # Cleanup: return to application tab.
    if popup_page is not None:
        popup_page.close()
        app_page.bring_to_front()
    elif app_page.url != current_url:
        app_page.go_back(wait_until="domcontentloaded")
        safe_wait_ui(app_page)
    return True


def build_report_template(login_url: Optional[str]) -> Dict[str, object]:
    return {
        "test_name": TEST_NAME,
        "run_at_utc": now_utc_iso(),
        "login_url_used": login_url or "",
        "overall_status": "FAIL",
        "steps": {field: StepResult().__dict__ for field in REPORT_FIELDS},
    }


def serialize_results(
    report: Dict[str, object],
    step_results: Dict[str, StepResult],
    output_dir: Path,
) -> Tuple[Path, Path]:
    report["steps"] = {key: value.__dict__ for key, value in step_results.items()}
    report["overall_status"] = "PASS" if all(v.status == "PASS" for v in step_results.values()) else "FAIL"

    json_path = output_dir / "final_report.json"
    md_path = output_dir / "final_report.md"

    with json_path.open("w", encoding="utf-8") as fp:
        json.dump(report, fp, indent=2, ensure_ascii=False)

    lines = [
        f"# {TEST_NAME} report",
        "",
        f"- Run at (UTC): {report['run_at_utc']}",
        f"- Overall status: **{report['overall_status']}**",
        "",
        "## Step results",
        "",
        "| Step | Status |",
        "|---|---|",
    ]
    for field in REPORT_FIELDS:
        lines.append(f"| {field} | {step_results[field].status} |")

    lines.append("")
    lines.append("## Evidence")
    lines.append("")
    for field in REPORT_FIELDS:
        result = step_results[field]
        lines.append(f"### {field}")
        if result.details:
            for detail in result.details:
                lines.append(f"- {detail}")
        if result.screenshots:
            for screenshot in result.screenshots:
                lines.append(f"- Screenshot: `{screenshot}`")
        if result.urls:
            for url in result.urls:
                lines.append(f"- URL: `{url}`")
        lines.append("")

    with md_path.open("w", encoding="utf-8") as fp:
        fp.write("\n".join(lines))

    return json_path, md_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio end-to-end workflow test")
    parser.add_argument("--login-url", default=None, help="SaleADS login URL of current environment")
    parser.add_argument("--account-email", default=DEFAULT_ACCOUNT_EMAIL, help="Google account email to select")
    parser.add_argument("--headed", action="store_true", help="Run browser in headed mode")
    parser.add_argument("--timeout-ms", type=int, default=20_000, help="Default action timeout in milliseconds")
    parser.add_argument(
        "--output-dir",
        default="automation/output/saleads_mi_negocio_full_test",
        help="Output directory for screenshots and reports",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    login_url = get_login_url(args.login_url)

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output_dir = Path(args.output_dir) / timestamp
    ensure_dir(output_dir)

    step_results = {field: StepResult() for field in REPORT_FIELDS}
    report = build_report_template(login_url)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=not args.headed,
            channel="chrome",
            args=["--disable-dev-shm-usage"],
        )
        context = browser.new_context(
            viewport={"width": 1600, "height": 1000},
            locale="es-ES",
        )
        context.set_default_timeout(args.timeout_ms)
        page = context.new_page()

        try:
            step1_ok = login_with_google(
                page=page,
                output_dir=output_dir,
                result=step_results["Login"],
                login_url=login_url,
                account_email=args.account_email,
            )
            if not step1_ok:
                append_prereq_failures(
                    step_results,
                    failed_after="Login",
                    reason="Login failed; downstream workflow requires authenticated app state.",
                )
            else:
                step2_ok = validate_mi_negocio_menu(page, output_dir, step_results["Mi Negocio menu"])
                if not step2_ok:
                    append_prereq_failures(
                        step_results,
                        failed_after="Mi Negocio menu",
                        reason="Could not open Mi Negocio menu to continue.",
                    )
                else:
                    step3_ok = validate_agregar_negocio_modal(page, output_dir, step_results["Agregar Negocio modal"])
                    if not step3_ok:
                        append_prereq_failures(
                            step_results,
                            failed_after="Agregar Negocio modal",
                            reason="Agregar Negocio modal validation failed.",
                        )
                    else:
                        step4_ok = validate_administrar_negocios_view(
                            page, output_dir, step_results["Administrar Negocios view"]
                        )
                        if not step4_ok:
                            append_prereq_failures(
                                step_results,
                                failed_after="Administrar Negocios view",
                                reason="Could not access Administrar Negocios page.",
                            )
                        else:
                            validate_informacion_general(page, step_results["Información General"])
                            validate_detalles_cuenta(page, step_results["Detalles de la Cuenta"])
                            validate_tus_negocios(page, step_results["Tus Negocios"])
                            open_legal_and_validate(
                                app_page=page,
                                output_dir=output_dir,
                                trigger_text="Términos y Condiciones",
                                expected_heading="Términos y Condiciones",
                                screenshot_label="08_terminos_y_condiciones",
                                result=step_results["Términos y Condiciones"],
                            )
                            open_legal_and_validate(
                                app_page=page,
                                output_dir=output_dir,
                                trigger_text="Política de Privacidad",
                                expected_heading="Política de Privacidad",
                                screenshot_label="09_politica_de_privacidad",
                                result=step_results["Política de Privacidad"],
                            )
        finally:
            context.close()
            browser.close()

    json_path, md_path = serialize_results(report, step_results, output_dir)

    summary_lines = [
        f"Report JSON: {json_path}",
        f"Report Markdown: {md_path}",
    ]
    for field in REPORT_FIELDS:
        summary_lines.append(f"{field}: {step_results[field].status}")

    print("\n".join(summary_lines))
    return 0 if all(step_results[field].status == "PASS" for field in REPORT_FIELDS) else 1


if __name__ == "__main__":
    sys.exit(main())
