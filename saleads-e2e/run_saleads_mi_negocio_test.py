#!/usr/bin/env python3
import json
import os
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from playwright.sync_api import Error, TimeoutError as PlaywrightTimeoutError, sync_playwright


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
    name: str
    status: str = "FAIL"
    details: list[str] = field(default_factory=list)
    artifacts: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)


class SaleAdsMiNegocioWorkflow:
    def __init__(self) -> None:
        self.login_url = os.getenv("SALEADS_LOGIN_URL", "").strip()
        self.google_account_email = os.getenv(
            "GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"
        ).strip()
        self.headless = os.getenv("HEADLESS", "true").lower() == "true"

        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        self.output_dir = Path(__file__).resolve().parent / "artifacts" / timestamp
        self.output_dir.mkdir(parents=True, exist_ok=True)

        self.results: dict[str, StepResult] = {
            field_name: StepResult(name=field_name) for field_name in REPORT_FIELDS
        }

    def _wait_ui(self, page) -> None:
        for load_state in ("domcontentloaded", "networkidle"):
            try:
                page.wait_for_load_state(load_state, timeout=7000)
            except PlaywrightTimeoutError:
                continue
        page.wait_for_timeout(900)

    def _screenshot(self, page, name: str, full_page: bool = False) -> str:
        safe_name = re.sub(r"[^a-zA-Z0-9_-]+", "_", name).strip("_").lower()
        file_path = self.output_dir / f"{safe_name}.png"
        page.screenshot(path=str(file_path), full_page=full_page)
        return str(file_path)

    def _find_visible_locator(self, page, candidates: list[str]):
        for text in candidates:
            locators = [
                page.get_by_role(
                    "button",
                    name=re.compile(rf"^{re.escape(text)}$", re.IGNORECASE),
                ).first,
                page.get_by_role(
                    "link",
                    name=re.compile(rf"^{re.escape(text)}$", re.IGNORECASE),
                ).first,
                page.get_by_text(
                    re.compile(rf"^{re.escape(text)}$", re.IGNORECASE)
                ).first,
                page.get_by_text(re.compile(re.escape(text), re.IGNORECASE)).first,
            ]
            for locator in locators:
                try:
                    if locator.is_visible(timeout=1800):
                        return locator, text
                except (PlaywrightTimeoutError, Error):
                    continue
        raise AssertionError(f"Could not find any visible element for {candidates}.")

    def _click_and_wait(self, page, candidates: list[str]) -> str:
        locator, matched_text = self._find_visible_locator(page, candidates)
        locator.click()
        self._wait_ui(page)
        return matched_text

    def _click_with_optional_new_tab(self, page, candidates: list[str]):
        locator, matched_text = self._find_visible_locator(page, candidates)
        try:
            with page.context.expect_page(timeout=5000) as popup_info:
                locator.click()
            new_page = popup_info.value
            self._wait_ui(new_page)
            return new_page, matched_text, True
        except PlaywrightTimeoutError:
            self._wait_ui(page)
            return page, matched_text, False

    def _is_text_visible(self, page, text: str, timeout_ms: int = 3500) -> bool:
        locators = [
            page.get_by_text(re.compile(rf"^{re.escape(text)}$", re.IGNORECASE)).first,
            page.get_by_text(re.compile(re.escape(text), re.IGNORECASE)).first,
        ]
        for locator in locators:
            try:
                if locator.is_visible(timeout=timeout_ms):
                    return True
            except (PlaywrightTimeoutError, Error):
                continue
        return False

    def _assert_text_visible(self, page, text: str) -> None:
        if not self._is_text_visible(page, text):
            raise AssertionError(f'Expected visible text "{text}" was not found.')

    def _assert_any_text_visible(self, page, options: list[str], label: str) -> None:
        for option in options:
            if self._is_text_visible(page, option):
                return
        raise AssertionError(f"Expected {label} from {options} was not found.")

    def _mark_pass(
        self,
        field_name: str,
        details: list[str] | None = None,
        artifacts: list[str] | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> None:
        result = self.results[field_name]
        result.status = "PASS"
        if details:
            result.details.extend(details)
        if artifacts:
            result.artifacts.extend(artifacts)
        if metadata:
            result.metadata.update(metadata)

    def _mark_fail(self, field_name: str, reason: str) -> None:
        result = self.results[field_name]
        result.status = "FAIL"
        result.details.append(reason)

    def _run(self) -> None:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=self.headless)
            context = browser.new_context()
            page = context.new_page()

            if not self.login_url:
                raise ValueError(
                    "SALEADS_LOGIN_URL env var is required and must point to the current SaleADS login page."
                )

            page.goto(self.login_url, wait_until="domcontentloaded")
            self._wait_ui(page)

            # Step 1 - Login with Google
            try:
                sidebar_is_visible = self._is_text_visible(page, "Mi Negocio") or self._is_text_visible(
                    page, "Negocio"
                )
                if not sidebar_is_visible:
                    auth_page, _, opened_popup = self._click_with_optional_new_tab(
                        page,
                        [
                            "Sign in with Google",
                            "Iniciar sesión con Google",
                            "Continuar con Google",
                            "Google",
                            "Login with Google",
                        ],
                    )
                    if self._is_text_visible(auth_page, self.google_account_email, timeout_ms=5000):
                        self._click_and_wait(auth_page, [self.google_account_email])
                        if opened_popup:
                            page.bring_to_front()
                            self._wait_ui(page)
                    elif self._is_text_visible(page, self.google_account_email, timeout_ms=5000):
                        self._click_and_wait(page, [self.google_account_email])

                self._assert_any_text_visible(
                    page,
                    ["Mi Negocio", "Negocio", "Administrar Negocios"],
                    "left sidebar navigation",
                )
                shot = self._screenshot(page, "step_1_dashboard_loaded")
                self._mark_pass(
                    "Login",
                    details=[
                        "Main application interface loaded after Google login flow.",
                        "Left sidebar navigation is visible.",
                    ],
                    artifacts=[shot],
                )
            except Exception as exc:
                fail_shot = self._screenshot(page, "step_1_login_failure")
                self._mark_fail("Login", str(exc))
                self.results["Login"].artifacts.append(fail_shot)

            # Step 2 - Open Mi Negocio menu
            try:
                self._click_and_wait(page, ["Negocio"])
                self._click_and_wait(page, ["Mi Negocio"])
                self._assert_text_visible(page, "Agregar Negocio")
                self._assert_text_visible(page, "Administrar Negocios")
                shot = self._screenshot(page, "step_2_mi_negocio_menu_expanded")
                self._mark_pass(
                    "Mi Negocio menu",
                    details=[
                        "Mi Negocio submenu expanded successfully.",
                        "Agregar Negocio and Administrar Negocios are visible.",
                    ],
                    artifacts=[shot],
                )
            except Exception as exc:
                fail_shot = self._screenshot(page, "step_2_mi_negocio_menu_failure")
                self._mark_fail("Mi Negocio menu", str(exc))
                self.results["Mi Negocio menu"].artifacts.append(fail_shot)

            # Step 3 - Validate Agregar Negocio modal
            try:
                self._click_and_wait(page, ["Agregar Negocio"])
                self._assert_text_visible(page, "Crear Nuevo Negocio")
                self._assert_text_visible(page, "Nombre del Negocio")
                self._assert_text_visible(page, "Tienes 2 de 3 negocios")
                self._assert_text_visible(page, "Cancelar")
                self._assert_text_visible(page, "Crear Negocio")

                # Optional actions.
                try:
                    business_input = page.get_by_label(
                        re.compile(r"Nombre del Negocio", re.IGNORECASE)
                    ).first
                    if business_input.is_visible(timeout=2000):
                        business_input.fill("Negocio Prueba Automatización")
                except (PlaywrightTimeoutError, Error):
                    pass

                shot = self._screenshot(page, "step_3_agregar_negocio_modal")
                self._click_and_wait(page, ["Cancelar"])
                self._mark_pass(
                    "Agregar Negocio modal",
                    details=[
                        "Crear Nuevo Negocio modal displayed with expected controls and text.",
                        "Optional field interaction completed and modal closed with Cancelar.",
                    ],
                    artifacts=[shot],
                )
            except Exception as exc:
                fail_shot = self._screenshot(page, "step_3_modal_failure")
                self._mark_fail("Agregar Negocio modal", str(exc))
                self.results["Agregar Negocio modal"].artifacts.append(fail_shot)

            # Step 4 - Open Administrar Negocios
            try:
                if not self._is_text_visible(page, "Administrar Negocios"):
                    self._click_and_wait(page, ["Mi Negocio"])

                self._click_and_wait(page, ["Administrar Negocios"])
                self._assert_text_visible(page, "Información General")
                self._assert_text_visible(page, "Detalles de la Cuenta")
                self._assert_text_visible(page, "Tus Negocios")
                self._assert_any_text_visible(
                    page,
                    ["Sección Legal", "Legal", "Términos y Condiciones"],
                    "legal section",
                )
                shot = self._screenshot(page, "step_4_administrar_negocios", full_page=True)
                self._mark_pass(
                    "Administrar Negocios view",
                    details=[
                        "Account page loaded with Información General, Detalles de la Cuenta, Tus Negocios and legal section.",
                    ],
                    artifacts=[shot],
                )
            except Exception as exc:
                fail_shot = self._screenshot(page, "step_4_administrar_failure", full_page=True)
                self._mark_fail("Administrar Negocios view", str(exc))
                self.results["Administrar Negocios view"].artifacts.append(fail_shot)

            # Step 5 - Validate Información General
            try:
                page_text = page.locator("body").inner_text()
                if not re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", page_text):
                    raise AssertionError("No visible user email found in Información General.")

                if not (
                    self._is_text_visible(page, "Nombre")
                    or self._is_text_visible(page, "Usuario")
                    or self._is_text_visible(page, "Name")
                ):
                    raise AssertionError(
                        "Could not verify user name label in Información General."
                    )

                self._assert_text_visible(page, "BUSINESS PLAN")
                self._assert_text_visible(page, "Cambiar Plan")
                self._mark_pass(
                    "Información General",
                    details=[
                        "User name/user label and user email are visible.",
                        "BUSINESS PLAN and Cambiar Plan are visible.",
                    ],
                )
            except Exception as exc:
                fail_shot = self._screenshot(page, "step_5_informacion_general_failure")
                self._mark_fail("Información General", str(exc))
                self.results["Información General"].artifacts.append(fail_shot)

            # Step 6 - Validate Detalles de la Cuenta
            try:
                self._assert_text_visible(page, "Cuenta creada")
                self._assert_any_text_visible(
                    page, ["Estado activo", "Estado Activo"], "active status"
                )
                self._assert_text_visible(page, "Idioma seleccionado")
                self._mark_pass(
                    "Detalles de la Cuenta",
                    details=[
                        "Cuenta creada, Estado activo and Idioma seleccionado are visible.",
                    ],
                )
            except Exception as exc:
                fail_shot = self._screenshot(page, "step_6_detalles_cuenta_failure")
                self._mark_fail("Detalles de la Cuenta", str(exc))
                self.results["Detalles de la Cuenta"].artifacts.append(fail_shot)

            # Step 7 - Validate Tus Negocios
            try:
                self._assert_text_visible(page, "Tus Negocios")
                self._assert_text_visible(page, "Agregar Negocio")
                self._assert_text_visible(page, "Tienes 2 de 3 negocios")
                self._mark_pass(
                    "Tus Negocios",
                    details=[
                        "Business list section, Agregar Negocio button and quota text are visible.",
                    ],
                )
            except Exception as exc:
                fail_shot = self._screenshot(page, "step_7_tus_negocios_failure")
                self._mark_fail("Tus Negocios", str(exc))
                self.results["Tus Negocios"].artifacts.append(fail_shot)

            # Step 8 - Validate Términos y Condiciones
            try:
                legal_page, _, opened_new_tab = self._click_with_optional_new_tab(
                    page, ["Términos y Condiciones"]
                )
                self._assert_text_visible(legal_page, "Términos y Condiciones")
                legal_text = legal_page.locator("body").inner_text()
                if len(legal_text.strip()) < 120:
                    raise AssertionError("Legal content for Términos y Condiciones appears too short.")
                shot = self._screenshot(legal_page, "step_8_terminos_y_condiciones")
                final_url = legal_page.url

                if opened_new_tab:
                    legal_page.close()
                    page.bring_to_front()
                    self._wait_ui(page)
                elif legal_page is page and page.url != self.login_url:
                    page.go_back(wait_until="domcontentloaded")
                    self._wait_ui(page)

                self._mark_pass(
                    "Términos y Condiciones",
                    details=["Términos y Condiciones page validated."],
                    artifacts=[shot],
                    metadata={"final_url": final_url},
                )
            except Exception as exc:
                fail_shot = self._screenshot(page, "step_8_terminos_failure")
                self._mark_fail("Términos y Condiciones", str(exc))
                self.results["Términos y Condiciones"].artifacts.append(fail_shot)

            # Step 9 - Validate Política de Privacidad
            try:
                legal_page, _, opened_new_tab = self._click_with_optional_new_tab(
                    page, ["Política de Privacidad"]
                )
                self._assert_text_visible(legal_page, "Política de Privacidad")
                legal_text = legal_page.locator("body").inner_text()
                if len(legal_text.strip()) < 120:
                    raise AssertionError("Legal content for Política de Privacidad appears too short.")
                shot = self._screenshot(legal_page, "step_9_politica_de_privacidad")
                final_url = legal_page.url

                if opened_new_tab:
                    legal_page.close()
                    page.bring_to_front()
                    self._wait_ui(page)
                elif legal_page is page and page.url != self.login_url:
                    page.go_back(wait_until="domcontentloaded")
                    self._wait_ui(page)

                self._mark_pass(
                    "Política de Privacidad",
                    details=["Política de Privacidad page validated."],
                    artifacts=[shot],
                    metadata={"final_url": final_url},
                )
            except Exception as exc:
                fail_shot = self._screenshot(page, "step_9_politica_failure")
                self._mark_fail("Política de Privacidad", str(exc))
                self.results["Política de Privacidad"].artifacts.append(fail_shot)

            browser.close()

    def _write_reports(self) -> None:
        report = {
            "test_name": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "environment": {
                "login_url": self.login_url,
                "headless": self.headless,
                "google_account_email": self.google_account_email,
            },
            "results": {
                field_name: {
                    "status": self.results[field_name].status,
                    "details": self.results[field_name].details,
                    "artifacts": self.results[field_name].artifacts,
                    "metadata": self.results[field_name].metadata,
                }
                for field_name in REPORT_FIELDS
            },
            "summary": {
                "passed": sum(
                    1 for field_name in REPORT_FIELDS if self.results[field_name].status == "PASS"
                ),
                "failed": sum(
                    1 for field_name in REPORT_FIELDS if self.results[field_name].status == "FAIL"
                ),
            },
        }

        json_report = self.output_dir / "final_report.json"
        json_report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

        lines = [
            "# SaleADS Mi Negocio Workflow Report",
            "",
            f"- Generated at (UTC): {report['generated_at_utc']}",
            f"- Login URL: {self.login_url}",
            f"- Headless: {self.headless}",
            "",
            "| Validation Step | Status |",
            "| --- | --- |",
        ]
        for field_name in REPORT_FIELDS:
            lines.append(f"| {field_name} | {self.results[field_name].status} |")
        lines.append("")
        lines.append("## Evidence")
        lines.append("")
        for field_name in REPORT_FIELDS:
            step_result = self.results[field_name]
            lines.append(f"### {field_name}")
            if step_result.details:
                for detail in step_result.details:
                    lines.append(f"- {detail}")
            if step_result.artifacts:
                for artifact in step_result.artifacts:
                    lines.append(f"- Screenshot: `{artifact}`")
            if "final_url" in step_result.metadata:
                lines.append(f"- Final URL: `{step_result.metadata['final_url']}`")
            lines.append("")

        markdown_report = self.output_dir / "final_report.md"
        markdown_report.write_text("\n".join(lines), encoding="utf-8")

        print(f"JSON report: {json_report}")
        print(f"Markdown report: {markdown_report}")
        for field_name in REPORT_FIELDS:
            print(f"{field_name}: {self.results[field_name].status}")

    def execute(self) -> int:
        try:
            self._run()
        except Exception as exc:
            self._mark_fail("Login", f"Fatal run error: {exc}")
        self._write_reports()
        return 0 if all(self.results[field].status == "PASS" for field in REPORT_FIELDS) else 1


if __name__ == "__main__":
    workflow = SaleAdsMiNegocioWorkflow()
    raise SystemExit(workflow.execute())
