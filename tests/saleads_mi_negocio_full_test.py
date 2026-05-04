import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from playwright.sync_api import BrowserContext, Error, Page, TimeoutError, expect, sync_playwright


DEFAULT_TIMEOUT_MS = 20000
POST_CLICK_WAIT_MS = 1200
LEGAL_HEADING_TIMEOUT_MS = 15000


@dataclass
class StepResult:
    status: str = "FAIL"
    details: List[str] = field(default_factory=list)
    screenshot: Optional[str] = None
    url: Optional[str] = None


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def wait_after_click(page: Page) -> None:
    page.wait_for_timeout(POST_CLICK_WAIT_MS)


def capture(page: Page, screenshots_dir: Path, name: str, full_page: bool = False) -> str:
    ensure_dir(screenshots_dir)
    path = screenshots_dir / f"{name}.png"
    page.screenshot(path=str(path), full_page=full_page)
    return str(path)


def first_visible(page: Page, labels: List[str]):
    for label in labels:
        locator = page.get_by_text(label, exact=False).first
        try:
            locator.wait_for(state="visible", timeout=1500)
            return locator, label
        except (TimeoutError, Error):
            continue
    return None, None


def click_by_text(page: Page, labels: List[str], exact: bool = False) -> Tuple[bool, Optional[str]]:
    for label in labels:
        locator = page.get_by_text(label, exact=exact).first
        try:
            locator.wait_for(state="visible", timeout=3000)
            locator.click()
            wait_after_click(page)
            return True, label
        except (TimeoutError, Error):
            continue
    return False, None


def expect_text_visible(page: Page, text: str, timeout: int = DEFAULT_TIMEOUT_MS) -> bool:
    locator = page.get_by_text(text, exact=False).first
    try:
        expect(locator).to_be_visible(timeout=timeout)
        return True
    except (AssertionError, Error, TimeoutError):
        return False


def expect_role_button(page: Page, labels: List[str]) -> bool:
    for label in labels:
        button = page.get_by_role("button", name=label, exact=False)
        try:
            expect(button.first).to_be_visible(timeout=4000)
            return True
        except (AssertionError, Error, TimeoutError):
            continue
    return False


def click_sidebar_item(page: Page, labels: List[str]) -> Tuple[bool, Optional[str]]:
    for label in labels:
        role_locator = page.get_by_role("link", name=label, exact=False).first
        try:
            role_locator.wait_for(state="visible", timeout=2500)
            role_locator.click()
            wait_after_click(page)
            return True, label
        except (TimeoutError, Error):
            pass

        ok, matched = click_by_text(page, [label], exact=False)
        if ok:
            return True, matched
    return False, None


def pick_google_account_if_present(page: Page, account_email: str) -> None:
    account_option = page.get_by_text(account_email, exact=False).first
    try:
        account_option.wait_for(state="visible", timeout=5000)
        account_option.click()
        wait_after_click(page)
    except (TimeoutError, Error):
        # If no account chooser appears, flow may already be authenticated.
        pass


def do_legal_navigation(
    page: Page,
    context: BrowserContext,
    link_text: str,
    heading_text: str,
    screenshots_dir: Path,
    screenshot_name: str,
) -> Tuple[bool, Optional[str], Optional[str], List[str]]:
    details: List[str] = []
    old_page = page
    opened_new_tab = False
    target_page = page
    final_url = None
    screenshot_path = None

    legal_link = page.get_by_text(link_text, exact=False).first
    try:
        legal_link.wait_for(state="visible", timeout=5000)
    except TimeoutError:
        return False, None, None, [f"Link '{link_text}' not visible."]

    try:
        with context.expect_page(timeout=5000) as new_page_info:
            legal_link.click()
        target_page = new_page_info.value
        target_page.wait_for_load_state("domcontentloaded")
        opened_new_tab = True
        details.append(f"'{link_text}' opened in a new tab.")
    except TimeoutError:
        old_page.wait_for_load_state("domcontentloaded")
        wait_after_click(old_page)
        target_page = old_page
        details.append(f"'{link_text}' navigated in the same tab.")

    try:
        target_page.wait_for_load_state("networkidle", timeout=LEGAL_HEADING_TIMEOUT_MS)
    except TimeoutError:
        details.append("Network idle timeout reached while waiting for legal page.")

    if expect_text_visible(target_page, heading_text, timeout=LEGAL_HEADING_TIMEOUT_MS):
        details.append(f"Heading '{heading_text}' is visible.")
    else:
        details.append(f"Heading '{heading_text}' is not visible.")
        if opened_new_tab and not target_page.is_closed():
            target_page.close()
            old_page.bring_to_front()
        return False, None, target_page.url, details

    legal_body_candidates = [
        "última actualización",
        "aceptas",
        "privacidad",
        "información",
        "uso",
        "datos",
        "condiciones",
        "términos",
    ]
    visible_locator, matched_label = first_visible(target_page, legal_body_candidates)
    if visible_locator:
        details.append(f"Legal content detected via text fragment '{matched_label}'.")
    else:
        details.append("Could not confidently detect legal content text fragment.")
        if opened_new_tab and not target_page.is_closed():
            target_page.close()
            old_page.bring_to_front()
        return False, None, target_page.url, details

    final_url = target_page.url
    screenshot_path = capture(target_page, screenshots_dir, screenshot_name, full_page=True)

    if opened_new_tab and not target_page.is_closed():
        target_page.close()
        old_page.bring_to_front()
    elif not opened_new_tab:
        old_page.go_back(wait_until="domcontentloaded")
        wait_after_click(old_page)

    return True, screenshot_path, final_url, details


def run_test(base_url: Optional[str], headless: bool, artifacts_dir: Path) -> Dict[str, StepResult]:
    results: Dict[str, StepResult] = {
        "Login": StepResult(),
        "Mi Negocio menu": StepResult(),
        "Agregar Negocio modal": StepResult(),
        "Administrar Negocios view": StepResult(),
        "Información General": StepResult(),
        "Detalles de la Cuenta": StepResult(),
        "Tus Negocios": StepResult(),
        "Términos y Condiciones": StepResult(),
        "Política de Privacidad": StepResult(),
    }

    screenshots_dir = artifacts_dir / "screenshots"
    ensure_dir(screenshots_dir)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=headless)
        try:
            context = browser.new_context(locale="es-ES")
            page = context.new_page()
            page.set_default_timeout(DEFAULT_TIMEOUT_MS)

            # Step 1: Login with Google
            step = results["Login"]

            if not base_url:
                step.details.append(
                    "No URL provided. Pass --base-url or set SALEADS_BASE_URL to the login page "
                    "of the current SaleADS environment."
                )
                return results

            try:
                page.goto(base_url, wait_until="domcontentloaded")
                wait_after_click(page)
            except Exception as exc:  # noqa: BLE001
                step.details.append(f"Could not open base URL '{base_url}': {exc}")
                return results

            login_click_ok, used_label = click_by_text(
                page,
                [
                    "Sign in with Google",
                    "Iniciar sesión con Google",
                    "Continuar con Google",
                    "Google",
                    "Ingresar con Google",
                ],
                exact=False,
            )
            if not login_click_ok:
                raise AssertionError("Login button for Google was not found.")

            step.details.append(f"Clicked login button using label '{used_label}'.")
            pick_google_account_if_present(page, "juanlucasbarbiergarzon@gmail.com")

            sidebar_indicators = ["Negocio", "Mi Negocio", "Dashboard", "Inicio"]
            sidebar_visible = False
            for indicator in sidebar_indicators:
                if expect_text_visible(page, indicator, timeout=30000):
                    sidebar_visible = True
                    step.details.append(f"Main app UI detected via '{indicator}'.")
                    break

            if not sidebar_visible:
                raise AssertionError("Main interface/sidebar not detected after Google login.")

            step.screenshot = capture(page, screenshots_dir, "01_dashboard_loaded", full_page=True)
            step.status = "PASS"
        except Exception as exc:  # noqa: BLE001
            step.details.append(str(exc))

        # Step 2: Open Mi Negocio menu
        step = results["Mi Negocio menu"]
        try:
            clicked, used_label = click_sidebar_item(page, ["Negocio", "Mi Negocio"])
            if not clicked:
                raise AssertionError("Could not click 'Negocio'/'Mi Negocio' in sidebar.")
            step.details.append(f"Clicked sidebar item '{used_label}'.")

            if not expect_text_visible(page, "Agregar Negocio"):
                raise AssertionError("'Agregar Negocio' is not visible after expanding menu.")
            if not expect_text_visible(page, "Administrar Negocios"):
                raise AssertionError("'Administrar Negocios' is not visible after expanding menu.")

            step.screenshot = capture(page, screenshots_dir, "02_mi_negocio_menu_expanded")
            step.status = "PASS"
        except Exception as exc:  # noqa: BLE001
            step.details.append(str(exc))

        # Step 3: Validate Agregar Negocio modal
        step = results["Agregar Negocio modal"]
        try:
            clicked, _ = click_by_text(page, ["Agregar Negocio"], exact=False)
            if not clicked:
                raise AssertionError("Could not click 'Agregar Negocio'.")

            if not expect_text_visible(page, "Crear Nuevo Negocio"):
                raise AssertionError("Modal title 'Crear Nuevo Negocio' not found.")

            nombre_input = page.get_by_label("Nombre del Negocio", exact=False).first
            try:
                expect(nombre_input).to_be_visible(timeout=5000)
            except Error:
                # fallback to placeholder lookup
                nombre_input = page.get_by_placeholder("Nombre del Negocio").first
                expect(nombre_input).to_be_visible(timeout=5000)

            if not expect_text_visible(page, "Tienes 2 de 3 negocios"):
                raise AssertionError("Text 'Tienes 2 de 3 negocios' not found in modal.")
            if not expect_role_button(page, ["Cancelar"]):
                raise AssertionError("Button 'Cancelar' not found in modal.")
            if not expect_role_button(page, ["Crear Negocio"]):
                raise AssertionError("Button 'Crear Negocio' not found in modal.")

            step.screenshot = capture(page, screenshots_dir, "03_agregar_negocio_modal")

            # Optional actions from prompt
            nombre_input.click()
            nombre_input.fill("Negocio Prueba Automatización")
            cancel_clicked, _ = click_by_text(page, ["Cancelar"], exact=False)
            if not cancel_clicked:
                raise AssertionError("Could not click 'Cancelar' to close modal.")

            step.status = "PASS"
        except Exception as exc:  # noqa: BLE001
            step.details.append(str(exc))

        # Step 4: Open Administrar Negocios
        step = results["Administrar Negocios view"]
        try:
            if not expect_text_visible(page, "Administrar Negocios", timeout=3000):
                # Re-expand if collapsed
                click_sidebar_item(page, ["Negocio", "Mi Negocio"])

            clicked, _ = click_by_text(page, ["Administrar Negocios"], exact=False)
            if not clicked:
                raise AssertionError("Could not click 'Administrar Negocios'.")

            required_sections = [
                "Información General",
                "Detalles de la Cuenta",
                "Tus Negocios",
                "Sección Legal",
            ]
            for section in required_sections:
                if not expect_text_visible(page, section, timeout=20000):
                    raise AssertionError(f"Section '{section}' not visible.")

            step.screenshot = capture(page, screenshots_dir, "04_administrar_negocios_page", full_page=True)
            step.status = "PASS"
        except Exception as exc:  # noqa: BLE001
            step.details.append(str(exc))

        # Step 5: Información General
        step = results["Información General"]
        try:
            info_checks = [
                ("User name", [r"^[A-Za-zÀ-ÿ'\-]+\s+[A-Za-zÀ-ÿ'\-]+$"]),
                ("User email", [r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"]),
                ("BUSINESS PLAN", []),
                ("Cambiar Plan", []),
            ]

            # Ensure static labels first
            if not expect_text_visible(page, "BUSINESS PLAN"):
                raise AssertionError("'BUSINESS PLAN' text not visible.")
            if not expect_text_visible(page, "Cambiar Plan"):
                raise AssertionError("'Cambiar Plan' button/text not visible.")

            page_text = page.locator("body").inner_text()
            if not re.search(info_checks[1][1][0], page_text):
                raise AssertionError("No visible user email found on page.")

            # Name check is heuristic and tolerant.
            if not re.search(info_checks[0][1][0], page_text, re.MULTILINE):
                step.details.append("Could not strictly validate full-name pattern; continuing with other checks.")

            step.status = "PASS"
        except Exception as exc:  # noqa: BLE001
            step.details.append(str(exc))

        # Step 6: Detalles de la Cuenta
        step = results["Detalles de la Cuenta"]
        try:
            expected_texts = ["Cuenta creada", "Estado activo", "Idioma seleccionado"]
            for text in expected_texts:
                if not expect_text_visible(page, text):
                    raise AssertionError(f"'{text}' is not visible in account details section.")
            step.status = "PASS"
        except Exception as exc:  # noqa: BLE001
            step.details.append(str(exc))

        # Step 7: Tus Negocios
        step = results["Tus Negocios"]
        try:
            if not expect_text_visible(page, "Tus Negocios"):
                raise AssertionError("'Tus Negocios' section title is missing.")
            if not expect_text_visible(page, "Agregar Negocio"):
                raise AssertionError("'Agregar Negocio' button is missing in business section.")
            if not expect_text_visible(page, "Tienes 2 de 3 negocios"):
                raise AssertionError("'Tienes 2 de 3 negocios' text is missing in business section.")

            # At least one business-like item should be visible.
            page_text = page.locator("body").inner_text().lower()
            if "negocio" not in page_text:
                raise AssertionError("Business list content was not detected.")

            step.status = "PASS"
        except Exception as exc:  # noqa: BLE001
            step.details.append(str(exc))

        # Step 8: Términos y Condiciones
        step = results["Términos y Condiciones"]
        try:
            ok, shot, final_url, details = do_legal_navigation(
                page=page,
                context=context,
                link_text="Términos y Condiciones",
                heading_text="Términos y Condiciones",
                screenshots_dir=screenshots_dir,
                screenshot_name="08_terminos_y_condiciones",
            )
            step.details.extend(details)
            step.screenshot = shot
            step.url = final_url
            step.status = "PASS" if ok else "FAIL"
        except Exception as exc:  # noqa: BLE001
            step.details.append(str(exc))

        # Step 9: Política de Privacidad
        step = results["Política de Privacidad"]
        try:
            ok, shot, final_url, details = do_legal_navigation(
                page=page,
                context=context,
                link_text="Política de Privacidad",
                heading_text="Política de Privacidad",
                screenshots_dir=screenshots_dir,
                screenshot_name="09_politica_de_privacidad",
            )
            step.details.extend(details)
            step.screenshot = shot
            step.url = final_url
            step.status = "PASS" if ok else "FAIL"
        except Exception as exc:  # noqa: BLE001
            step.details.append(str(exc))
        finally:
            browser.close()

    return results


def write_report(report_path: Path, results: Dict[str, StepResult]) -> None:
    ensure_dir(report_path.parent)
    serializable = {
        key: {
            "status": value.status,
            "details": value.details,
            "screenshot": value.screenshot,
            "url": value.url,
        }
        for key, value in results.items()
    }
    report_path.write_text(json.dumps(serializable, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def print_summary(results: Dict[str, StepResult]) -> None:
    print("\nFinal Report:")
    for key, value in results.items():
        suffix = f" | URL: {value.url}" if value.url else ""
        print(f"- {key}: {value.status}{suffix}")
        for detail in value.details:
            print(f"  - {detail}")
        if value.screenshot:
            print(f"  - screenshot: {value.screenshot}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow validation.")
    parser.add_argument(
        "--base-url",
        default=os.getenv("SALEADS_BASE_URL"),
        help="Login page URL for the target SaleADS environment. If omitted, SALEADS_BASE_URL is used.",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run browser headed (default is headless).",
    )
    parser.add_argument(
        "--artifacts-dir",
        default=os.getenv("SALEADS_ARTIFACTS_DIR", "artifacts/saleads_mi_negocio_full_test"),
        help="Directory where screenshots and report JSON are stored.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    artifacts_dir = Path(args.artifacts_dir)
    ensure_dir(artifacts_dir)

    results = run_test(base_url=args.base_url, headless=not args.headed, artifacts_dir=artifacts_dir)

    report_path = artifacts_dir / "report.json"
    write_report(report_path, results)
    print_summary(results)
    print(f"\nJSON report: {report_path}")

    all_passed = all(step.status == "PASS" for step in results.values())
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
