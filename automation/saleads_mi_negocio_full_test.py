#!/usr/bin/env python3
import argparse
import json
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

from playwright.sync_api import Browser, BrowserContext, Locator, Page, Playwright, TimeoutError, sync_playwright


DEFAULT_ACCOUNT = "juanlucasbarbiergarzon@gmail.com"


@dataclass
class StepResult:
    name: str
    passed: bool
    details: str


class SaleadsMiNegocioWorkflowTest:
    def __init__(
        self,
        page: Page,
        artifacts_dir: Path,
        google_account: str,
    ) -> None:
        self.page = page
        self.artifacts_dir = artifacts_dir
        self.google_account = google_account
        self.results: Dict[str, StepResult] = {}
        self._step_counter = 0

    def run(self) -> Dict[str, StepResult]:
        self._run_step("Login", self.step_login_with_google)
        self._run_step("Mi Negocio menu", self.step_open_mi_negocio_menu)
        self._run_step("Agregar Negocio modal", self.step_validate_agregar_negocio_modal)
        self._run_step("Administrar Negocios view", self.step_open_administrar_negocios)
        self._run_step("Información General", self.step_validate_informacion_general)
        self._run_step("Detalles de la Cuenta", self.step_validate_detalles_cuenta)
        self._run_step("Tus Negocios", self.step_validate_tus_negocios)
        self._run_step("Términos y Condiciones", self.step_validate_terminos)
        self._run_step("Política de Privacidad", self.step_validate_privacidad)
        return self.results

    def _run_step(self, report_key: str, fn) -> None:
        try:
            ok, details = fn()
            self.results[report_key] = StepResult(report_key, ok, details)
        except Exception as exc:  # pylint: disable=broad-except
            self.results[report_key] = StepResult(report_key, False, f"Unhandled error: {exc}")

    def _next_shot_name(self, label: str) -> str:
        self._step_counter += 1
        safe = re.sub(r"[^a-zA-Z0-9_-]+", "_", label.strip().lower()).strip("_")
        return f"{self._step_counter:02d}_{safe}.png"

    def _take_screenshot(self, label: str, full_page: bool = False) -> str:
        name = self._next_shot_name(label)
        path = self.artifacts_dir / name
        self.page.screenshot(path=str(path), full_page=full_page)
        return str(path)

    def _wait_ui_settle(self, timeout_ms: int = 15000) -> None:
        try:
            self.page.wait_for_load_state("networkidle", timeout=timeout_ms)
        except TimeoutError:
            # Some SPA transitions never reach networkidle. DOMContentLoaded is a useful fallback.
            self.page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)

    def _first_visible(self, locators: Sequence[Locator], timeout_ms: int = 4000) -> Optional[Locator]:
        for locator in locators:
            try:
                locator.first.wait_for(state="visible", timeout=timeout_ms)
                return locator.first
            except TimeoutError:
                continue
        return None

    def _visible_text_candidates(self, text: str) -> List[Locator]:
        escaped = re.escape(text)
        return [
            self.page.get_by_role("button", name=re.compile(escaped, re.IGNORECASE)),
            self.page.get_by_role("link", name=re.compile(escaped, re.IGNORECASE)),
            self.page.get_by_role("menuitem", name=re.compile(escaped, re.IGNORECASE)),
            self.page.get_by_text(re.compile(escaped, re.IGNORECASE)),
        ]

    def _click_by_visible_text(self, labels: Sequence[str], timeout_ms: int = 8000) -> Tuple[bool, str]:
        for label in labels:
            locator = self._first_visible(self._visible_text_candidates(label), timeout_ms=timeout_ms)
            if locator:
                locator.click()
                self._wait_ui_settle()
                return True, label
        return False, f"Could not find clickable label from: {labels}"

    def _assert_text_visible(self, labels: Sequence[str], timeout_ms: int = 6000) -> Tuple[bool, str]:
        for label in labels:
            locator = self._first_visible(self._visible_text_candidates(label), timeout_ms=timeout_ms)
            if locator:
                return True, label
        return False, f"None of these texts became visible: {labels}"

    def _expand_mi_negocio_if_needed(self) -> None:
        add_visible, _ = self._assert_text_visible(["Agregar Negocio"], timeout_ms=1500)
        admin_visible, _ = self._assert_text_visible(["Administrar Negocios"], timeout_ms=1500)
        if add_visible and admin_visible:
            return

        clicked, reason = self._click_by_visible_text(["Mi Negocio", "Negocio"])
        if not clicked:
            raise RuntimeError(f"Could not expand Mi Negocio menu: {reason}")

    def step_login_with_google(self) -> Tuple[bool, str]:
        # Assume login page is already open; optionally this script can still work if started elsewhere.
        context = self.page.context
        existing_pages = set(context.pages)

        clicked, reason = self._click_by_visible_text(
            [
                "Sign in with Google",
                "Iniciar sesion con Google",
                "Iniciar sesión con Google",
                "Continuar con Google",
                "Google",
            ],
            timeout_ms=12000,
        )
        if not clicked:
            return False, f"Login button not found: {reason}"

        popup = None
        deadline = time.time() + 8
        while time.time() < deadline:
            new_pages = [p for p in context.pages if p not in existing_pages]
            if new_pages:
                popup = new_pages[0]
                break
            time.sleep(0.2)

        if popup:
            popup.wait_for_load_state("domcontentloaded", timeout=15000)
            account_locator = self._first_visible(
                [
                    popup.get_by_text(self.google_account, exact=False),
                    popup.get_by_role("button", name=re.compile(re.escape(self.google_account), re.IGNORECASE)),
                    popup.get_by_role("link", name=re.compile(re.escape(self.google_account), re.IGNORECASE)),
                ],
                timeout_ms=8000,
            )
            if account_locator:
                account_locator.click()
            # Continue even if account picker did not appear (already authenticated scenarios).
            try:
                popup.wait_for_close(timeout=12000)
            except TimeoutError:
                pass

        # Wait for post-login app shell.
        self._wait_ui_settle(timeout_ms=20000)

        sidebar_ok, sidebar_text = self._assert_text_visible(
            ["Mi Negocio", "Negocio", "Dashboard", "Inicio"],
            timeout_ms=20000,
        )
        if not sidebar_ok:
            return False, "Main interface/sidebar did not appear after login."

        shot = self._take_screenshot("dashboard_loaded")
        return True, f"Dashboard loaded and sidebar visible via '{sidebar_text}'. Screenshot: {shot}"

    def step_open_mi_negocio_menu(self) -> Tuple[bool, str]:
        clicked, reason = self._click_by_visible_text(["Mi Negocio", "Negocio"])
        if not clicked:
            return False, f"Could not open Mi Negocio menu: {reason}"

        add_ok, _ = self._assert_text_visible(["Agregar Negocio"], timeout_ms=6000)
        admin_ok, _ = self._assert_text_visible(["Administrar Negocios"], timeout_ms=6000)
        if not (add_ok and admin_ok):
            return False, "Mi Negocio submenu did not show both expected options."

        shot = self._take_screenshot("mi_negocio_menu_expanded")
        return True, f"Expanded Mi Negocio menu validated. Screenshot: {shot}"

    def step_validate_agregar_negocio_modal(self) -> Tuple[bool, str]:
        clicked, reason = self._click_by_visible_text(["Agregar Negocio"])
        if not clicked:
            return False, f"Could not click 'Agregar Negocio': {reason}"

        # Modal validation points.
        title_ok, _ = self._assert_text_visible(["Crear Nuevo Negocio"], timeout_ms=8000)
        quota_ok, _ = self._assert_text_visible(["Tienes 2 de 3 negocios"], timeout_ms=8000)
        cancel_ok, _ = self._assert_text_visible(["Cancelar"], timeout_ms=8000)
        create_ok, _ = self._assert_text_visible(["Crear Negocio"], timeout_ms=8000)

        nombre_input = self._first_visible(
            [
                self.page.get_by_label(re.compile("Nombre del Negocio", re.IGNORECASE)),
                self.page.get_by_placeholder(re.compile("Nombre del Negocio", re.IGNORECASE)),
            ],
            timeout_ms=8000,
        )

        if not all([title_ok, quota_ok, cancel_ok, create_ok, nombre_input is not None]):
            return False, (
                "Agregar Negocio modal missing expected elements "
                "(title/input/quota/buttons)."
            )

        shot = self._take_screenshot("agregar_negocio_modal")

        # Optional action requested by workflow.
        nombre_input.fill("Negocio Prueba Automatizacion")
        cancel_clicked, _ = self._click_by_visible_text(["Cancelar"])
        if not cancel_clicked:
            return False, "Modal validated but could not close it with 'Cancelar'."

        return True, f"Agregar Negocio modal validated and closed. Screenshot: {shot}"

    def step_open_administrar_negocios(self) -> Tuple[bool, str]:
        self._expand_mi_negocio_if_needed()
        clicked, reason = self._click_by_visible_text(["Administrar Negocios"])
        if not clicked:
            return False, f"Could not click Administrar Negocios: {reason}"

        self._wait_ui_settle(timeout_ms=20000)
        checks = [
            "Informacion General",
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Seccion Legal",
            "Sección Legal",
        ]
        missing = []
        for label in checks:
            ok, _ = self._assert_text_visible([label], timeout_ms=4000)
            if not ok:
                missing.append(label)

        # We accept accented/non-accented variants by grouping.
        grouped_missing = []
        if not any(item in checks and item not in missing for item in ["Informacion General", "Información General"]):
            grouped_missing.append("Información General")
        if "Detalles de la Cuenta" in missing:
            grouped_missing.append("Detalles de la Cuenta")
        if "Tus Negocios" in missing:
            grouped_missing.append("Tus Negocios")
        if not any(item in checks and item not in missing for item in ["Seccion Legal", "Sección Legal"]):
            grouped_missing.append("Sección Legal")

        if grouped_missing:
            return False, f"Account page missing sections: {grouped_missing}"

        shot = self._take_screenshot("administrar_negocios_account_page", full_page=True)
        return True, f"Administrar Negocios view validated. Screenshot: {shot}"

    def step_validate_informacion_general(self) -> Tuple[bool, str]:
        checks = [
            ("User name", ["Nombre", "Usuario", "Perfil"]),
            ("User email", ["@", "Correo", "Email"]),
            ("BUSINESS PLAN", ["BUSINESS PLAN"]),
            ("Cambiar Plan button", ["Cambiar Plan"]),
        ]

        failures = []
        for label, options in checks:
            ok, _ = self._assert_text_visible(options, timeout_ms=6000)
            if not ok:
                failures.append(label)

        if failures:
            return False, f"Informacion General failed checks: {failures}"
        return True, "Informacion General section validated."

    def step_validate_detalles_cuenta(self) -> Tuple[bool, str]:
        checks = [
            "Cuenta creada",
            "Estado activo",
            "Idioma seleccionado",
        ]
        failures = []
        for text in checks:
            ok, _ = self._assert_text_visible([text], timeout_ms=6000)
            if not ok:
                failures.append(text)

        if failures:
            return False, f"Detalles de la Cuenta missing: {failures}"
        return True, "Detalles de la Cuenta validated."

    def step_validate_tus_negocios(self) -> Tuple[bool, str]:
        checks = [
            ("Business list", ["Tus Negocios"]),
            ("Agregar Negocio button", ["Agregar Negocio"]),
            ("Quota text", ["Tienes 2 de 3 negocios"]),
        ]
        failures = []
        for label, options in checks:
            ok, _ = self._assert_text_visible(options, timeout_ms=6000)
            if not ok:
                failures.append(label)

        if failures:
            return False, f"Tus Negocios failed checks: {failures}"
        return True, "Tus Negocios section validated."

    def _validate_legal_link(
        self,
        link_labels: Sequence[str],
        expected_heading: str,
        screenshot_label: str,
    ) -> Tuple[bool, str]:
        context = self.page.context
        start_pages = set(context.pages)
        clicked, reason = self._click_by_visible_text(link_labels, timeout_ms=10000)
        if not clicked:
            return False, f"Could not click legal link {link_labels}: {reason}"

        target_page = self.page
        deadline = time.time() + 8
        while time.time() < deadline:
            new_pages = [p for p in context.pages if p not in start_pages]
            if new_pages:
                target_page = new_pages[0]
                break
            time.sleep(0.2)

        target_page.wait_for_load_state("domcontentloaded", timeout=20000)
        heading_visible = False
        for option in [expected_heading, expected_heading.replace("ó", "o")]:
            try:
                target_page.get_by_text(re.compile(re.escape(option), re.IGNORECASE)).first.wait_for(
                    state="visible",
                    timeout=12000,
                )
                heading_visible = True
                break
            except TimeoutError:
                continue

        if not heading_visible:
            return False, f"Heading '{expected_heading}' not visible on legal page."

        body_locator = target_page.locator("body")
        try:
            body_locator.wait_for(state="visible", timeout=8000)
            body_text = body_locator.inner_text(timeout=8000).strip()
        except TimeoutError:
            body_text = ""

        if len(body_text) < 120:
            return False, f"Legal content for '{expected_heading}' appears too short."

        shot_path = self.artifacts_dir / self._next_shot_name(screenshot_label)
        target_page.screenshot(path=str(shot_path), full_page=True)
        final_url = target_page.url

        # Cleanup: return to app tab/page.
        if target_page != self.page:
            target_page.close()
            self.page.bring_to_front()
            self._wait_ui_settle()
        else:
            self.page.go_back(wait_until="domcontentloaded")
            self._wait_ui_settle()

        return True, f"Validated '{expected_heading}' at URL: {final_url}. Screenshot: {shot_path}"

    def step_validate_terminos(self) -> Tuple[bool, str]:
        return self._validate_legal_link(
            link_labels=["Términos y Condiciones", "Terminos y Condiciones"],
            expected_heading="Términos y Condiciones",
            screenshot_label="terminos_y_condiciones",
        )

    def step_validate_privacidad(self) -> Tuple[bool, str]:
        return self._validate_legal_link(
            link_labels=["Política de Privacidad", "Politica de Privacidad"],
            expected_heading="Política de Privacidad",
            screenshot_label="politica_de_privacidad",
        )


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="SaleADS Mi Negocio full workflow test (login + module validations)."
    )
    parser.add_argument(
        "--base-url",
        default=None,
        help="Optional login URL for the current environment. If omitted, assumes active page is already on login.",
    )
    parser.add_argument(
        "--google-account",
        default=DEFAULT_ACCOUNT,
        help="Google account email to select in account chooser.",
    )
    parser.add_argument(
        "--artifacts-dir",
        default="artifacts/saleads_mi_negocio_full_test",
        help="Directory where screenshots and JSON report will be saved.",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run browser in headed mode (default is headless).",
    )
    return parser


def setup_browser(base_url: Optional[str], headed: bool) -> Tuple[Playwright, Browser, BrowserContext, Page]:
    pw = sync_playwright().start()
    browser = pw.chromium.launch(headless=not headed)
    context = browser.new_context(viewport={"width": 1600, "height": 1000})
    page = context.new_page()

    if base_url:
        page.goto(base_url, wait_until="domcontentloaded")

    return pw, browser, context, page


def main() -> int:
    args = build_arg_parser().parse_args()
    artifacts_dir = Path(args.artifacts_dir)
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    browser: Optional[Browser] = None
    context: Optional[BrowserContext] = None
    page: Optional[Page] = None
    pw: Optional[Playwright] = None
    timestamp = datetime.utcnow().isoformat() + "Z"

    try:
        pw, browser, context, page = setup_browser(args.base_url, args.headed)
        if not args.base_url and page.url == "about:blank":
            raise RuntimeError(
                "No starting URL provided. Pass --base-url to open the SaleADS login page."
            )

        runner = SaleadsMiNegocioWorkflowTest(
            page=page,
            artifacts_dir=artifacts_dir,
            google_account=args.google_account,
        )
        results = runner.run()

        report = {
            "name": "saleads_mi_negocio_full_test",
            "executed_at": timestamp,
            "artifacts_dir": str(artifacts_dir),
            "summary": {
                key: {"status": "PASS" if value.passed else "FAIL", "details": value.details}
                for key, value in results.items()
            },
            "overall_status": "PASS" if all(item.passed for item in results.values()) else "FAIL",
        }

        report_path = artifacts_dir / "final_report.json"
        report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(json.dumps(report, indent=2, ensure_ascii=False))
        print(f"\nReport written to: {report_path}")
        return 0 if report["overall_status"] == "PASS" else 1
    except Exception as exc:  # pylint: disable=broad-except
        print(f"Fatal error: {exc}", file=sys.stderr)
        return 2
    finally:
        if context:
            context.close()
        if browser:
            browser.close()
        if pw:
            pw.stop()


if __name__ == "__main__":
    sys.exit(main())
