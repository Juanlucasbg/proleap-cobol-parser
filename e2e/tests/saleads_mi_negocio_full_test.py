#!/usr/bin/env python3
"""End-to-end validation for SaleADS Mi Negocio workflow.

This script is intentionally environment-agnostic:
- It does not hardcode any SaleADS domain.
- It can start from any SaleADS login URL supplied at runtime.
- It relies primarily on visible text selectors in Spanish.
"""

from __future__ import annotations

import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from playwright.sync_api import BrowserContext, Locator, Page, TimeoutError, sync_playwright


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


class MiNegocioWorkflowTest:
    def __init__(self) -> None:
        self.start_url = os.getenv("SALEADS_START_URL", "").strip()
        self.google_email = os.getenv(
            "GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"
        ).strip()
        self.expected_user_name = os.getenv("SALEADS_EXPECTED_USER_NAME", "").strip()
        self.headless = os.getenv("HEADLESS", "true").lower() not in {"false", "0", "no"}
        self.timeout_ms = int(os.getenv("PW_TIMEOUT_MS", "30000"))
        self.output_dir = Path(os.getenv("SALEADS_E2E_OUTPUT_DIR", "e2e/artifacts"))
        self.screenshot_dir = self.output_dir / "screenshots"
        self.report_path = self.output_dir / "saleads_mi_negocio_full_test_report.json"
        self.results: Dict[str, str] = {field: "FAIL" for field in REPORT_FIELDS}
        self.screenshots: Dict[str, str] = {}
        self.legal_urls: Dict[str, str] = {}
        self.errors: List[str] = []
        self.page: Optional[Page] = None
        self.context: Optional[BrowserContext] = None

        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.screenshot_dir.mkdir(parents=True, exist_ok=True)

    def wait_after_ui_action(self, page: Page) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=self.timeout_ms)
        except TimeoutError:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=7000)
        except TimeoutError:
            pass
        page.wait_for_timeout(900)

    def is_visible(self, locator: Locator, timeout: int = 5000) -> bool:
        try:
            locator.first.wait_for(state="visible", timeout=timeout)
            return True
        except TimeoutError:
            return False

    def first_visible(self, candidates: List[Locator], timeout: int = 5000) -> Locator:
        for candidate in candidates:
            if self.is_visible(candidate, timeout=timeout):
                return candidate.first
        raise AssertionError("None of the expected visible locators appeared.")

    def click_visible(self, candidates: List[Locator], description: str) -> Locator:
        locator = self.first_visible(candidates)
        locator.click()
        self.wait_after_ui_action(self.page)  # type: ignore[arg-type]
        return locator

    def screenshot(self, key: str, page: Optional[Page] = None, full_page: bool = False) -> None:
        target_page = page or self.page
        if target_page is None:
            return
        path = self.screenshot_dir / f"{len(self.screenshots) + 1:02d}_{key}.png"
        target_page.screenshot(path=str(path), full_page=full_page)
        self.screenshots[key] = str(path)

    def run_step(self, field_name: str, step_fn) -> None:
        try:
            passed = bool(step_fn())
            self.results[field_name] = "PASS" if passed else "FAIL"
            if not passed:
                self.errors.append(f"{field_name}: validation returned false.")
        except Exception as exc:  # noqa: BLE001
            self.results[field_name] = "FAIL"
            self.errors.append(f"{field_name}: {exc}")

    def _infer_user_name_visible(self, page: Page) -> bool:
        body_text = page.locator("body").inner_text(timeout=self.timeout_ms)
        lines = [line.strip() for line in body_text.splitlines() if line.strip()]
        filtered_label_pattern = re.compile(
            r"informaci[oó]n general|business plan|detalles de la cuenta|"
            r"cuenta creada|estado activo|idioma seleccionado|tus negocios",
            re.IGNORECASE,
        )
        email_pattern = re.compile(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}")

        for idx, line in enumerate(lines):
            if email_pattern.search(line):
                search_start = max(0, idx - 4)
                for candidate in lines[search_start:idx]:
                    if (
                        len(candidate) >= 3
                        and not email_pattern.search(candidate)
                        and not filtered_label_pattern.search(candidate)
                    ):
                        return True
        return False

    def _validate_legal_page(self, legal_page: Page, heading_text: str) -> Tuple[bool, str]:
        heading_visible = self.is_visible(
            legal_page.get_by_role("heading", name=re.compile(heading_text, re.IGNORECASE)),
            timeout=12000,
        ) or self.is_visible(
            legal_page.get_by_text(re.compile(heading_text, re.IGNORECASE), exact=False),
            timeout=12000,
        )
        body_text = legal_page.locator("body").inner_text(timeout=self.timeout_ms).strip()
        legal_content_visible = len(body_text.split()) >= 20
        return heading_visible and legal_content_visible, legal_page.url

    def _open_legal_link(self, link_text: str, heading_text: str, screenshot_key: str) -> Tuple[bool, str]:
        if self.page is None or self.context is None:
            raise AssertionError("Page/context not initialized.")

        app_page = self.page
        link_locator = self.first_visible(
            [
                app_page.get_by_role("link", name=re.compile(link_text, re.IGNORECASE)),
                app_page.get_by_text(re.compile(link_text, re.IGNORECASE), exact=False),
            ],
            timeout=10000,
        )

        opened_new_tab = False
        legal_page = app_page

        try:
            with self.context.expect_page(timeout=7000) as page_info:
                link_locator.click()
            legal_page = page_info.value
            opened_new_tab = True
            self.wait_after_ui_action(legal_page)
        except TimeoutError:
            link_locator.click()
            self.wait_after_ui_action(app_page)
            legal_page = app_page

        valid, final_url = self._validate_legal_page(legal_page, heading_text)
        self.screenshot(screenshot_key, page=legal_page, full_page=True)

        if opened_new_tab:
            legal_page.close()
            app_page.bring_to_front()
            self.wait_after_ui_action(app_page)
        else:
            app_page.go_back(wait_until="domcontentloaded")
            self.wait_after_ui_action(app_page)

        return valid, final_url

    def step_login(self) -> bool:
        assert self.page is not None
        assert self.context is not None

        if self.start_url:
            self.page.goto(self.start_url, wait_until="domcontentloaded", timeout=self.timeout_ms)
            self.wait_after_ui_action(self.page)

        login_button = self.first_visible(
            [
                self.page.get_by_role(
                    "button",
                    name=re.compile(
                        r"sign in with google|iniciar sesi[oó]n con google|continuar con google|google",
                        re.IGNORECASE,
                    ),
                ),
                self.page.get_by_text(
                    re.compile(
                        r"sign in with google|iniciar sesi[oó]n con google|continuar con google",
                        re.IGNORECASE,
                    ),
                    exact=False,
                ),
            ],
            timeout=12000,
        )

        existing_pages = set(self.context.pages)
        login_button.click()
        self.wait_after_ui_action(self.page)

        google_page: Optional[Page] = None
        for candidate in self.context.pages:
            if candidate not in existing_pages:
                google_page = candidate
                break

        if google_page is not None:
            if self.is_visible(google_page.get_by_text(self.google_email, exact=True), timeout=9000):
                google_page.get_by_text(self.google_email, exact=True).click()
                self.wait_after_ui_action(google_page)
            try:
                google_page.wait_for_event("close", timeout=25000)
            except TimeoutError:
                pass
            self.page.bring_to_front()
        else:
            if self.is_visible(self.page.get_by_text(self.google_email, exact=True), timeout=5000):
                self.page.get_by_text(self.google_email, exact=True).click()
                self.wait_after_ui_action(self.page)

        main_ui_visible = self.is_visible(
            self.page.locator("aside"),
            timeout=25000,
        ) or self.is_visible(
            self.page.get_by_text(re.compile(r"mi negocio|dashboard|inicio", re.IGNORECASE)),
            timeout=25000,
        )

        sidebar_visible = self.is_visible(self.page.locator("aside"), timeout=10000) or self.is_visible(
            self.page.get_by_text(re.compile(r"negocio", re.IGNORECASE), exact=False),
            timeout=10000,
        )

        if main_ui_visible and sidebar_visible:
            self.screenshot("dashboard_loaded")
            return True
        return False

    def step_open_mi_negocio_menu(self) -> bool:
        assert self.page is not None

        self.click_visible(
            [
                self.page.get_by_role("link", name=re.compile(r"mi negocio", re.IGNORECASE)),
                self.page.get_by_role("button", name=re.compile(r"mi negocio", re.IGNORECASE)),
                self.page.get_by_text(re.compile(r"mi negocio", re.IGNORECASE), exact=False),
            ],
            "Mi Negocio",
        )

        agregar_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"agregar negocio", re.IGNORECASE), exact=False),
            timeout=8000,
        )
        administrar_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"administrar negocios", re.IGNORECASE), exact=False),
            timeout=8000,
        )

        if agregar_visible and administrar_visible:
            self.screenshot("mi_negocio_expanded_menu")
            return True
        return False

    def step_validate_agregar_negocio_modal(self) -> bool:
        assert self.page is not None

        self.click_visible(
            [
                self.page.get_by_role("link", name=re.compile(r"agregar negocio", re.IGNORECASE)),
                self.page.get_by_role("button", name=re.compile(r"agregar negocio", re.IGNORECASE)),
                self.page.get_by_text(re.compile(r"agregar negocio", re.IGNORECASE), exact=False),
            ],
            "Agregar Negocio",
        )

        title_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"crear nuevo negocio", re.IGNORECASE), exact=False),
            timeout=12000,
        )
        name_input = self.page.get_by_label(re.compile(r"nombre del negocio", re.IGNORECASE))
        name_input_visible = self.is_visible(name_input, timeout=12000) or self.is_visible(
            self.page.get_by_placeholder(re.compile(r"nombre del negocio", re.IGNORECASE)),
            timeout=12000,
        )
        quota_text_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"tienes\s*2\s*de\s*3\s*negocios", re.IGNORECASE), exact=False),
            timeout=12000,
        )
        cancel_visible = self.is_visible(
            self.page.get_by_role("button", name=re.compile(r"cancelar", re.IGNORECASE)),
            timeout=12000,
        )
        create_visible = self.is_visible(
            self.page.get_by_role("button", name=re.compile(r"crear negocio", re.IGNORECASE)),
            timeout=12000,
        )

        self.screenshot("agregar_negocio_modal")

        if name_input_visible:
            if self.is_visible(name_input, timeout=1000):
                name_input.fill("Negocio Prueba Automatización")
            else:
                self.page.get_by_placeholder(re.compile(r"nombre del negocio", re.IGNORECASE)).fill(
                    "Negocio Prueba Automatización"
                )

        if cancel_visible:
            self.page.get_by_role("button", name=re.compile(r"cancelar", re.IGNORECASE)).click()
            self.wait_after_ui_action(self.page)

        return all([title_visible, name_input_visible, quota_text_visible, cancel_visible, create_visible])

    def step_open_administrar_negocios(self) -> bool:
        assert self.page is not None

        administrar_locator = self.page.get_by_text(
            re.compile(r"administrar negocios", re.IGNORECASE), exact=False
        )
        if not self.is_visible(administrar_locator, timeout=4000):
            self.click_visible(
                [
                    self.page.get_by_role("link", name=re.compile(r"mi negocio", re.IGNORECASE)),
                    self.page.get_by_role("button", name=re.compile(r"mi negocio", re.IGNORECASE)),
                    self.page.get_by_text(re.compile(r"mi negocio", re.IGNORECASE), exact=False),
                ],
                "Mi Negocio (expand again)",
            )

        self.click_visible(
            [
                self.page.get_by_role("link", name=re.compile(r"administrar negocios", re.IGNORECASE)),
                self.page.get_by_role("button", name=re.compile(r"administrar negocios", re.IGNORECASE)),
                administrar_locator,
            ],
            "Administrar Negocios",
        )

        info_general = self.is_visible(
            self.page.get_by_text(re.compile(r"informaci[oó]n general", re.IGNORECASE), exact=False),
            timeout=15000,
        )
        detalles = self.is_visible(
            self.page.get_by_text(re.compile(r"detalles de la cuenta", re.IGNORECASE), exact=False),
            timeout=15000,
        )
        tus_negocios = self.is_visible(
            self.page.get_by_text(re.compile(r"tus negocios", re.IGNORECASE), exact=False),
            timeout=15000,
        )
        legal = self.is_visible(
            self.page.get_by_text(re.compile(r"secci[oó]n legal", re.IGNORECASE), exact=False),
            timeout=15000,
        )

        if all([info_general, detalles, tus_negocios, legal]):
            self.screenshot("administrar_negocios_page", full_page=True)
            return True
        return False

    def step_validate_informacion_general(self) -> bool:
        assert self.page is not None

        business_plan_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"business plan", re.IGNORECASE), exact=False),
            timeout=10000,
        )
        change_plan_visible = self.is_visible(
            self.page.get_by_role("button", name=re.compile(r"cambiar plan", re.IGNORECASE)),
            timeout=10000,
        ) or self.is_visible(
            self.page.get_by_role("link", name=re.compile(r"cambiar plan", re.IGNORECASE)),
            timeout=10000,
        )

        email_visible = self.is_visible(
            self.page.get_by_text(
                re.compile(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}", re.IGNORECASE),
                exact=False,
            ),
            timeout=10000,
        )

        if self.expected_user_name:
            user_name_visible = self.is_visible(
                self.page.get_by_text(re.compile(re.escape(self.expected_user_name), re.IGNORECASE), exact=False),
                timeout=8000,
            )
        else:
            user_name_visible = self._infer_user_name_visible(self.page)

        return all([user_name_visible, email_visible, business_plan_visible, change_plan_visible])

    def step_validate_detalles_cuenta(self) -> bool:
        assert self.page is not None
        created_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"cuenta creada", re.IGNORECASE), exact=False),
            timeout=10000,
        )
        active_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"estado activo", re.IGNORECASE), exact=False),
            timeout=10000,
        )
        language_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"idioma seleccionado", re.IGNORECASE), exact=False),
            timeout=10000,
        )
        return all([created_visible, active_visible, language_visible])

    def step_validate_tus_negocios(self) -> bool:
        assert self.page is not None
        list_heading_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"tus negocios", re.IGNORECASE), exact=False),
            timeout=10000,
        )
        add_button_visible = self.is_visible(
            self.page.get_by_role("button", name=re.compile(r"agregar negocio", re.IGNORECASE)),
            timeout=10000,
        ) or self.is_visible(
            self.page.get_by_role("link", name=re.compile(r"agregar negocio", re.IGNORECASE)),
            timeout=10000,
        )
        quota_text_visible = self.is_visible(
            self.page.get_by_text(re.compile(r"tienes\s*2\s*de\s*3\s*negocios", re.IGNORECASE), exact=False),
            timeout=10000,
        )
        business_rows_visible = self.is_visible(
            self.page.locator("main li, main [role='listitem'], main table tbody tr"),
            timeout=6000,
        )
        return all([list_heading_visible, add_button_visible, quota_text_visible, business_rows_visible])

    def step_validate_terminos(self) -> bool:
        valid, url = self._open_legal_link(
            link_text="Términos y Condiciones",
            heading_text="Términos y Condiciones",
            screenshot_key="terminos_y_condiciones",
        )
        self.legal_urls["Términos y Condiciones"] = url
        return valid

    def step_validate_politica(self) -> bool:
        valid, url = self._open_legal_link(
            link_text="Política de Privacidad",
            heading_text="Política de Privacidad",
            screenshot_key="politica_de_privacidad",
        )
        self.legal_urls["Política de Privacidad"] = url
        return valid

    def write_report(self) -> None:
        report_payload = {
            "test_name": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "config": {
                "saleads_start_url": self.start_url or None,
                "google_account_email": self.google_email,
                "headless": self.headless,
                "timeout_ms": self.timeout_ms,
            },
            "results": self.results,
            "legal_urls": self.legal_urls,
            "screenshots": self.screenshots,
            "errors": self.errors,
            "overall_status": "PASS" if all(v == "PASS" for v in self.results.values()) else "FAIL",
        }
        self.report_path.write_text(json.dumps(report_payload, indent=2, ensure_ascii=False), encoding="utf-8")

        print("\nFinal Report")
        print("============")
        for field in REPORT_FIELDS:
            print(f"- {field}: {self.results[field]}")
        if self.legal_urls:
            print("\nLegal URLs")
            print("----------")
            for key, value in self.legal_urls.items():
                print(f"- {key}: {value}")
        print(f"\nJSON report: {self.report_path}")
        if self.errors:
            print("\nErrors")
            print("------")
            for error in self.errors:
                print(f"- {error}")

    def execute(self) -> int:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=self.headless)
            self.context = browser.new_context(viewport={"width": 1440, "height": 900})
            self.page = self.context.new_page()
            self.page.set_default_timeout(self.timeout_ms)

            self.run_step("Login", self.step_login)
            self.run_step("Mi Negocio menu", self.step_open_mi_negocio_menu)
            self.run_step("Agregar Negocio modal", self.step_validate_agregar_negocio_modal)
            self.run_step("Administrar Negocios view", self.step_open_administrar_negocios)
            self.run_step("Información General", self.step_validate_informacion_general)
            self.run_step("Detalles de la Cuenta", self.step_validate_detalles_cuenta)
            self.run_step("Tus Negocios", self.step_validate_tus_negocios)
            self.run_step("Términos y Condiciones", self.step_validate_terminos)
            self.run_step("Política de Privacidad", self.step_validate_politica)

            browser.close()

        self.write_report()
        return 0 if all(v == "PASS" for v in self.results.values()) else 1


def main() -> int:
    test_runner = MiNegocioWorkflowTest()
    return test_runner.execute()


if __name__ == "__main__":
    sys.exit(main())
