#!/usr/bin/env python3
"""
End-to-end browser test for the SaleADS "Mi Negocio" workflow.

The flow is environment agnostic:
- no hardcoded domain
- prefers visible text selectors
- supports either an already-opened browser page (via CDP) or a provided start URL
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Callable

from playwright.sync_api import Locator
from playwright.sync_api import Page
from playwright.sync_api import Playwright
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright


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
    status: str
    details: list[str]


class SaleadsMiNegocioWorkflow:
    def __init__(self, page: Page, artifacts_dir: Path, account_email: str) -> None:
        self.page = page
        self.account_email = account_email
        self.artifacts_dir = artifacts_dir
        self.results: dict[str, StepResult] = {
            key: StepResult(status="FAIL", details=[]) for key in REPORT_FIELDS
        }
        self.legal_urls: dict[str, str] = {}
        self.screenshots: list[str] = []
        self.screenshot_index = 1

    def _log(self, message: str) -> None:
        print(f"[saleads-mi-negocio] {message}", flush=True)

    def _sanitize(self, value: str) -> str:
        safe = re.sub(r"[^a-zA-Z0-9_-]+", "_", value.strip().lower())
        return safe.strip("_") or "checkpoint"

    def wait_for_ui(self, page: Page | None = None) -> None:
        current_page = page or self.page
        try:
            current_page.wait_for_load_state("domcontentloaded", timeout=15000)
        except PlaywrightTimeoutError:
            pass
        try:
            current_page.wait_for_load_state("networkidle", timeout=15000)
        except PlaywrightTimeoutError:
            pass
        current_page.wait_for_timeout(600)

    def take_screenshot(self, name: str, full_page: bool = False, page: Page | None = None) -> str:
        current_page = page or self.page
        filename = f"{self.screenshot_index:02d}_{self._sanitize(name)}.png"
        self.screenshot_index += 1
        path = self.artifacts_dir / filename
        current_page.screenshot(path=str(path), full_page=full_page)
        self.screenshots.append(str(path))
        self._log(f"Screenshot saved: {path}")
        return str(path)

    def _locators_for_text(self, text: str, page: Page | None = None) -> list[Locator]:
        current_page = page or self.page
        escaped = re.escape(text)
        text_pattern = re.compile(escaped, re.IGNORECASE)
        return [
            current_page.get_by_role("button", name=text_pattern),
            current_page.get_by_role("link", name=text_pattern),
            current_page.get_by_role("menuitem", name=text_pattern),
            current_page.get_by_role("tab", name=text_pattern),
            current_page.get_by_text(text_pattern),
        ]

    def click_by_visible_text(
        self, text_candidates: list[str], wait_after_click: bool = True, page: Page | None = None
    ) -> str:
        current_page = page or self.page
        last_error: Exception | None = None

        for text in text_candidates:
            for locator in self._locators_for_text(text, current_page):
                target = locator.first
                try:
                    target.wait_for(state="visible", timeout=2500)
                    target.click()
                    if wait_after_click:
                        self.wait_for_ui(current_page)
                    self._log(f"Clicked text target: {text}")
                    return text
                except Exception as exc:  # noqa: BLE001
                    last_error = exc

        raise AssertionError(
            f"Unable to click any candidate text: {text_candidates}. Last error: {last_error}"
        )

    def _is_locator_visible(self, locator: Locator, timeout_ms: int = 5000) -> bool:
        try:
            locator.first.wait_for(state="visible", timeout=timeout_ms)
            return True
        except Exception:  # noqa: BLE001
            return False

    def is_any_text_visible(self, text_candidates: list[str], timeout_ms: int = 6000, page: Page | None = None) -> bool:
        current_page = page or self.page
        deadline = time.time() + (timeout_ms / 1000.0)
        while time.time() < deadline:
            for text in text_candidates:
                for locator in self._locators_for_text(text, current_page):
                    if self._is_locator_visible(locator, timeout_ms=400):
                        return True
            time.sleep(0.2)
        return False

    def assert_text_visible(self, text_candidates: list[str], description: str, page: Page | None = None) -> None:
        if not self.is_any_text_visible(text_candidates, page=page):
            raise AssertionError(f"Expected visible text not found for {description}: {text_candidates}")

    def run_step(self, label: str, action: Callable[[], None]) -> None:
        self._log(f"Running step: {label}")
        try:
            action()
            self.results[label].status = "PASS"
        except Exception as exc:  # noqa: BLE001
            self.results[label].status = "FAIL"
            self.results[label].details.append(str(exc))
            self._log(f"Step failed [{label}]: {exc}")
            try:
                self.take_screenshot(f"{label}_failure", full_page=True)
            except Exception as screenshot_exc:  # noqa: BLE001
                self._log(f"Failure screenshot could not be captured: {screenshot_exc}")

    def _maybe_click_google_account(self, popup_page: Page) -> None:
        self.wait_for_ui(popup_page)
        if self.is_any_text_visible([self.account_email], page=popup_page):
            self.click_by_visible_text([self.account_email], wait_after_click=True, page=popup_page)
            return

        # Fallback for account chooser with strict equality spacing differences.
        candidate = popup_page.get_by_text(re.compile(re.escape(self.account_email), re.IGNORECASE)).first
        if self._is_locator_visible(candidate, timeout_ms=4000):
            candidate.click()
            self.wait_for_ui(popup_page)

    def step_login(self) -> None:
        context = self.page.context
        previous_pages = set(context.pages)
        self.click_by_visible_text(
            [
                "Sign in with Google",
                "Iniciar sesión con Google",
                "Iniciar sesion con Google",
                "Continuar con Google",
                "Ingresar con Google",
                "Login with Google",
            ],
            wait_after_click=False,
        )

        popup_page: Page | None = None
        deadline = time.time() + 8
        while time.time() < deadline:
            current_pages = set(context.pages)
            new_pages = [candidate for candidate in current_pages if candidate not in previous_pages]
            if new_pages:
                popup_page = new_pages[0]
                break
            time.sleep(0.2)

        if popup_page is not None:
            self._log("Google login popup detected")
            self._maybe_click_google_account(popup_page)
            # Wait for popup to close or finish redirect.
            try:
                popup_page.wait_for_event("close", timeout=15000)
            except PlaywrightTimeoutError:
                pass

        # If account chooser appears in same tab, try selecting the requested account.
        if self.is_any_text_visible([self.account_email]):
            self.click_by_visible_text([self.account_email])

        self.wait_for_ui(self.page)

        main_app_visible = (
            self._is_locator_visible(self.page.locator("main"), timeout_ms=7000)
            or self._is_locator_visible(self.page.locator("aside"), timeout_ms=7000)
            or self._is_locator_visible(self.page.get_by_role("navigation"), timeout_ms=7000)
        )
        if not main_app_visible:
            raise AssertionError("Main application interface was not detected after login")

        sidebar_visible = (
            self._is_locator_visible(self.page.locator("aside"), timeout_ms=7000)
            or self._is_locator_visible(self.page.get_by_role("navigation"), timeout_ms=7000)
            or self.is_any_text_visible(["Negocio", "Mi Negocio"], timeout_ms=7000)
        )
        if not sidebar_visible:
            raise AssertionError("Left sidebar navigation was not detected after login")

        self.take_screenshot("dashboard_loaded")

    def step_mi_negocio_menu(self) -> None:
        if self.is_any_text_visible(["Negocio"], timeout_ms=3000):
            self.click_by_visible_text(["Negocio"])

        self.click_by_visible_text(["Mi Negocio", "Mi negocio"])

        self.assert_text_visible(["Agregar Negocio"], "Mi Negocio expanded submenu item Agregar Negocio")
        self.assert_text_visible(
            ["Administrar Negocios", "Administrar negocios"],
            "Mi Negocio expanded submenu item Administrar Negocios",
        )
        self.take_screenshot("mi_negocio_menu_expanded")

    def step_agregar_negocio_modal(self) -> None:
        self.click_by_visible_text(["Agregar Negocio"])

        self.assert_text_visible(["Crear Nuevo Negocio"], "Agregar Negocio modal title")
        self.assert_text_visible(["Nombre del Negocio"], "Agregar Negocio input label")
        self.assert_text_visible(
            ["Tienes 2 de 3 negocios", "Tienes 2 de 3 negocio"],
            "Agregar Negocio quota text",
        )
        self.assert_text_visible(["Cancelar"], "Agregar Negocio modal cancel button")
        self.assert_text_visible(["Crear Negocio"], "Agregar Negocio modal create button")

        self.take_screenshot("agregar_negocio_modal")

        name_input_candidates = [
            self.page.get_by_label("Nombre del Negocio", exact=False),
            self.page.get_by_placeholder("Nombre del Negocio"),
            self.page.locator("input[name*='negocio' i], input[id*='negocio' i]"),
        ]
        for locator in name_input_candidates:
            if self._is_locator_visible(locator, timeout_ms=1500):
                locator.first.click()
                locator.first.fill("Negocio Prueba Automatización")
                break

        self.click_by_visible_text(["Cancelar"])

    def step_administrar_negocios_view(self) -> None:
        if not self.is_any_text_visible(["Administrar Negocios"], timeout_ms=2500):
            self.click_by_visible_text(["Mi Negocio", "Mi negocio"])

        self.click_by_visible_text(["Administrar Negocios", "Administrar negocios"])
        self.wait_for_ui()

        self.assert_text_visible(["Información General", "Informacion General"], "account section Información General")
        self.assert_text_visible(["Detalles de la Cuenta", "Detalles de la cuenta"], "account section Detalles")
        self.assert_text_visible(["Tus Negocios", "Tus negocios"], "account section Tus Negocios")
        self.assert_text_visible(["Sección Legal", "Seccion Legal", "Legal"], "account section legal")
        self.take_screenshot("administrar_negocios_account_page", full_page=True)

    def step_informacion_general(self) -> None:
        self.assert_text_visible(["Información General", "Informacion General"], "Información General heading")
        self.assert_text_visible(["BUSINESS PLAN"], "Business plan label")
        self.assert_text_visible(["Cambiar Plan", "Change Plan"], "Cambiar Plan button")

        body_text = self.page.locator("body").inner_text()
        has_email = bool(re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", body_text))
        if not has_email:
            raise AssertionError("No visible user email detected in Información General")

        has_name_label = self.is_any_text_visible(["Nombre", "Usuario", "User"])
        has_person_name = bool(re.search(r"\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+\b", body_text))
        if not (has_name_label or has_person_name):
            raise AssertionError("No visible user name signal detected in Información General")

    def step_detalles_cuenta(self) -> None:
        self.assert_text_visible(["Detalles de la Cuenta", "Detalles de la cuenta"], "Detalles heading")
        self.assert_text_visible(["Cuenta creada"], "Cuenta creada text")
        self.assert_text_visible(["Estado activo", "Activo"], "Estado activo text")
        self.assert_text_visible(["Idioma seleccionado", "Idioma"], "Idioma seleccionado text")

    def step_tus_negocios(self) -> None:
        self.assert_text_visible(["Tus Negocios", "Tus negocios"], "Tus Negocios heading")
        self.assert_text_visible(["Agregar Negocio"], "Tus Negocios add button")
        self.assert_text_visible(["Tienes 2 de 3 negocios"], "Tus Negocios quota")

        container = self.page.locator("section,div").filter(
            has_text=re.compile(r"Tus\s+Negocios", re.IGNORECASE)
        ).first
        if not self._is_locator_visible(container, timeout_ms=3000):
            raise AssertionError("Tus Negocios container is not visible")

        list_items = container.locator("li, [role='listitem'], table tbody tr, article, .card")
        if list_items.count() > 0:
            return

        # Generic fallback: ensure section has non-trivial content beyond static labels.
        section_text = container.inner_text().strip()
        if len(section_text) < 70:
            raise AssertionError("Business list content not detected in Tus Negocios section")

    def _open_legal_link_and_validate(self, link_text: str, heading_text: str, report_label: str) -> None:
        context = self.page.context
        previous_pages = set(context.pages)
        self.click_by_visible_text([link_text], wait_after_click=False)

        target_page = self.page
        deadline = time.time() + 8
        while time.time() < deadline:
            current_pages = set(context.pages)
            new_pages = [candidate for candidate in current_pages if candidate not in previous_pages]
            if new_pages:
                target_page = new_pages[0]
                break
            time.sleep(0.2)

        self.wait_for_ui(target_page)
        self.assert_text_visible([heading_text], f"{report_label} heading", page=target_page)

        page_text = target_page.locator("body").inner_text().strip()
        if len(page_text) < 200:
            raise AssertionError(f"{report_label} legal content seems too short")

        self.take_screenshot(self._sanitize(report_label), page=target_page)
        self.legal_urls[report_label] = target_page.url

        if target_page != self.page:
            target_page.close()
            self.page.bring_to_front()
            self.wait_for_ui(self.page)
        else:
            # Same-tab navigation fallback.
            self.page.go_back(wait_until="domcontentloaded")
            self.wait_for_ui(self.page)

    def step_terminos(self) -> None:
        self._open_legal_link_and_validate(
            link_text="Términos y Condiciones",
            heading_text="Términos y Condiciones",
            report_label="Términos y Condiciones",
        )

    def step_politica(self) -> None:
        self._open_legal_link_and_validate(
            link_text="Política de Privacidad",
            heading_text="Política de Privacidad",
            report_label="Política de Privacidad",
        )

    def run_all(self) -> dict[str, object]:
        self.run_step("Login", self.step_login)
        self.run_step("Mi Negocio menu", self.step_mi_negocio_menu)
        self.run_step("Agregar Negocio modal", self.step_agregar_negocio_modal)
        self.run_step("Administrar Negocios view", self.step_administrar_negocios_view)
        self.run_step("Información General", self.step_informacion_general)
        self.run_step("Detalles de la Cuenta", self.step_detalles_cuenta)
        self.run_step("Tus Negocios", self.step_tus_negocios)
        self.run_step("Términos y Condiciones", self.step_terminos)
        self.run_step("Política de Privacidad", self.step_politica)

        report = {
            "test_name": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.utcnow().isoformat() + "Z",
            "results": {
                key: {
                    "status": self.results[key].status,
                    "details": self.results[key].details,
                }
                for key in REPORT_FIELDS
            },
            "legal_urls": self.legal_urls,
            "screenshots": self.screenshots,
            "overall_status": "PASS"
            if all(self.results[key].status == "PASS" for key in REPORT_FIELDS)
            else "FAIL",
        }
        return report


def _create_page(playwright: Playwright, args: argparse.Namespace) -> tuple[Page, object]:
    if args.cdp_endpoint:
        browser = playwright.chromium.connect_over_cdp(args.cdp_endpoint)
        if browser.contexts:
            context = browser.contexts[0]
        else:
            context = browser.new_context()
        page = context.pages[0] if context.pages else context.new_page()
        return page, browser

    browser = playwright.chromium.launch(headless=not args.headed)
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    page = context.new_page()
    return page, browser


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow validation")
    parser.add_argument(
        "--start-url",
        default=None,
        help="Login page URL for the active SaleADS environment. Not hardcoded by this test.",
    )
    parser.add_argument(
        "--cdp-endpoint",
        default=None,
        help="Optional CDP endpoint to attach to an already-open browser session.",
    )
    parser.add_argument(
        "--account-email",
        default="juanlucasbarbiergarzon@gmail.com",
        help="Google account email to select when account chooser appears.",
    )
    parser.add_argument(
        "--artifacts-dir",
        default="artifacts/saleads_mi_negocio_full_test",
        help="Directory where screenshots and final report are written.",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run with visible browser window (default is headless).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
    artifacts_dir = Path(args.artifacts_dir) / timestamp
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        page, browser = _create_page(playwright, args)
        try:
            if args.start_url:
                page.goto(args.start_url, wait_until="domcontentloaded", timeout=45000)
            elif page.url in ("about:blank", "data:,"):
                raise RuntimeError(
                    "No start URL was provided and no preloaded page was available. "
                    "Use --start-url or --cdp-endpoint connected to an open SaleADS login page."
                )

            workflow = SaleadsMiNegocioWorkflow(
                page=page,
                artifacts_dir=artifacts_dir,
                account_email=args.account_email,
            )
            final_report = workflow.run_all()

            report_path = artifacts_dir / "final_report.json"
            report_path.write_text(json.dumps(final_report, ensure_ascii=False, indent=2), encoding="utf-8")
            print(json.dumps(final_report, ensure_ascii=False, indent=2))
            print(f"\nFinal report path: {report_path}")

            return 0 if final_report["overall_status"] == "PASS" else 1
        finally:
            browser.close()


if __name__ == "__main__":
    sys.exit(main())
