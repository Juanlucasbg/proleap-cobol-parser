#!/usr/bin/env python3
"""Cross-environment SaleADS Mi Negocio workflow validation with Playwright.

This script validates:
1) Login with Google and dashboard/sidebar visibility.
2) Mi Negocio menu expansion and submenu items.
3) Agregar Negocio modal.
4) Administrar Negocios account view sections.
5) Informacion General section.
6) Detalles de la Cuenta section.
7) Tus Negocios section.
8) Terminos y Condiciones link behavior/content (+ URL evidence).
9) Politica de Privacidad link behavior/content (+ URL evidence).
10) Final PASS/FAIL report for all required fields.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional

from playwright.sync_api import BrowserContext, Locator, Page, Playwright, TimeoutError, sync_playwright


TEST_NAME = "saleads_mi_negocio_full_test"
DEFAULT_ACCOUNT = "juanlucasbarbiergarzon@gmail.com"

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
    details: str = ""
    screenshots: List[str] = field(default_factory=list)
    final_url: Optional[str] = None


class SaleadsMiNegocioWorkflow:
    def __init__(
        self,
        page: Page,
        context: BrowserContext,
        output_dir: Path,
        account_email: str,
        action_timeout_ms: int,
    ) -> None:
        self.page = page
        self.context = context
        self.output_dir = output_dir
        self.account_email = account_email
        self.action_timeout_ms = action_timeout_ms
        self.results: Dict[str, StepResult] = {field: StepResult() for field in REPORT_FIELDS}

    def run(self) -> Dict[str, StepResult]:
        self._step_login()
        self._step_open_mi_negocio_menu()
        self._step_validate_agregar_negocio_modal()
        self._step_open_administrar_negocios()
        self._step_validate_informacion_general()
        self._step_validate_detalles_cuenta()
        self._step_validate_tus_negocios()
        self._step_validate_terminos()
        self._step_validate_politica()
        return self.results

    def _mark_result(
        self,
        key: str,
        passed: bool,
        details: str,
        screenshots: Optional[List[str]] = None,
        final_url: Optional[str] = None,
    ) -> None:
        self.results[key].status = "PASS" if passed else "FAIL"
        self.results[key].details = details
        self.results[key].screenshots = screenshots or []
        self.results[key].final_url = final_url

    def _wait_for_ui(self, page: Optional[Page] = None) -> None:
        active_page = page or self.page
        active_page.wait_for_load_state("domcontentloaded", timeout=self.action_timeout_ms)
        try:
            active_page.wait_for_load_state("networkidle", timeout=5_000)
        except TimeoutError:
            # Some SPAs keep long-running requests. DOM readiness is enough for this test.
            pass
        active_page.wait_for_timeout(750)

    def _capture(self, label: str, page: Optional[Page] = None, full_page: bool = False) -> str:
        safe = re.sub(r"[^a-zA-Z0-9_-]+", "_", label).strip("_").lower() or "checkpoint"
        filename = f"{safe}.png"
        destination = self.output_dir / filename
        (page or self.page).screenshot(path=str(destination), full_page=full_page)
        return str(destination)

    def _first_visible(self, locators: List[Locator], timeout_per_locator: int = 2_500) -> Optional[Locator]:
        for locator in locators:
            try:
                candidate = locator.first
                candidate.wait_for(state="visible", timeout=timeout_per_locator)
                return candidate
            except TimeoutError:
                continue
        return None

    def _is_visible(self, locator: Locator, timeout_ms: int = 3_000) -> bool:
        try:
            locator.first.wait_for(state="visible", timeout=timeout_ms)
            return True
        except TimeoutError:
            return False

    def _click_and_wait(self, locator: Locator) -> None:
        locator.click()
        self._wait_for_ui()

    def _by_text_locators(self, text_regex: str) -> List[Locator]:
        pattern = re.compile(text_regex, re.IGNORECASE)
        return [
            self.page.get_by_role("button", name=pattern),
            self.page.get_by_role("link", name=pattern),
            self.page.get_by_role("menuitem", name=pattern),
            self.page.get_by_text(pattern),
        ]

    def _click_by_text(self, text_regex: str, required: bool = True) -> bool:
        target = self._first_visible(self._by_text_locators(text_regex))
        if not target:
            if required:
                raise RuntimeError(f"Could not find clickable element matching /{text_regex}/i")
            return False
        self._click_and_wait(target)
        return True

    def _step_login(self) -> None:
        key = "Login"
        screenshots: List[str] = []
        try:
            self._wait_for_ui()

            sidebar_already_visible = self._first_visible(
                [
                    self.page.get_by_role("navigation"),
                    self.page.locator("aside"),
                    self.page.get_by_text(re.compile(r"\bnegocio\b", re.IGNORECASE)),
                ],
                timeout_per_locator=2_000,
            )

            if not sidebar_already_visible:
                self._click_by_text(r"sign in with google|iniciar sesi[oó]n con google|continuar con google")

                account_chooser = self._first_visible(
                    [
                        self.page.get_by_role("button", name=re.compile(re.escape(self.account_email), re.IGNORECASE)),
                        self.page.get_by_text(re.compile(re.escape(self.account_email), re.IGNORECASE)),
                    ],
                    timeout_per_locator=5_000,
                )
                if account_chooser:
                    self._click_and_wait(account_chooser)

            self._wait_for_ui()
            app_interface_visible = self._first_visible(
                [
                    self.page.get_by_role("navigation"),
                    self.page.locator("aside"),
                    self.page.get_by_text(re.compile(r"\bnegocio\b", re.IGNORECASE)),
                ],
                timeout_per_locator=6_000,
            )
            sidebar_visible = self._first_visible(
                [
                    self.page.get_by_role("navigation"),
                    self.page.locator("aside"),
                ],
                timeout_per_locator=4_000,
            )

            screenshots.append(self._capture("01_dashboard_loaded"))
            passed = bool(app_interface_visible and sidebar_visible)
            details = "Main interface and left sidebar are visible." if passed else "Main interface or sidebar was not visible after login."
            self._mark_result(key, passed, details, screenshots=screenshots)
        except Exception as exc:  # noqa: BLE001
            screenshots.append(self._capture("01_login_failure_state"))
            self._mark_result(key, False, f"Login workflow failed: {exc}", screenshots=screenshots)

    def _step_open_mi_negocio_menu(self) -> None:
        key = "Mi Negocio menu"
        screenshots: List[str] = []
        try:
            self._click_by_text(r"\bnegocio\b", required=False)
            self._click_by_text(r"mi negocio")

            agregar_visible = self._is_visible(self.page.get_by_text(re.compile(r"agregar negocio", re.IGNORECASE)))
            admin_visible = self._is_visible(self.page.get_by_text(re.compile(r"administrar negocios", re.IGNORECASE)))
            expanded = agregar_visible and admin_visible

            screenshots.append(self._capture("02_mi_negocio_expanded_menu"))
            details = "Mi Negocio submenu expanded with both options visible." if expanded else "Submenu did not show all expected options."
            self._mark_result(key, expanded, details, screenshots=screenshots)
        except Exception as exc:  # noqa: BLE001
            screenshots.append(self._capture("02_mi_negocio_menu_failure"))
            self._mark_result(key, False, f"Could not validate Mi Negocio menu: {exc}", screenshots=screenshots)

    def _step_validate_agregar_negocio_modal(self) -> None:
        key = "Agregar Negocio modal"
        screenshots: List[str] = []
        try:
            self._click_by_text(r"agregar negocio")

            title = self.page.get_by_text(re.compile(r"crear nuevo negocio", re.IGNORECASE))
            nombre_input = self.page.get_by_label(re.compile(r"nombre del negocio", re.IGNORECASE))
            cuota_text = self.page.get_by_text(re.compile(r"tienes\s*2\s*de\s*3\s*negocios", re.IGNORECASE))
            cancelar = self.page.get_by_role("button", name=re.compile(r"cancelar", re.IGNORECASE))
            crear = self.page.get_by_role("button", name=re.compile(r"crear negocio", re.IGNORECASE))

            validations = [
                self._is_visible(title),
                self._is_visible(nombre_input),
                self._is_visible(cuota_text),
                self._is_visible(cancelar),
                self._is_visible(crear),
            ]

            # Capture evidence while the modal is still open.
            screenshots.append(self._capture("03_agregar_negocio_modal"))

            if self._is_visible(nombre_input, timeout_ms=2_000):
                nombre_input.fill("Negocio Prueba Automatización")
            if self._is_visible(cancelar, timeout_ms=2_000):
                self._click_and_wait(cancelar)

            passed = all(validations)
            details = "Agregar Negocio modal validated successfully." if passed else "One or more required modal elements were not visible."
            self._mark_result(key, passed, details, screenshots=screenshots)
        except Exception as exc:  # noqa: BLE001
            screenshots.append(self._capture("03_agregar_negocio_modal_failure"))
            self._mark_result(key, False, f"Could not validate Agregar Negocio modal: {exc}", screenshots=screenshots)

    def _step_open_administrar_negocios(self) -> None:
        key = "Administrar Negocios view"
        screenshots: List[str] = []
        try:
            self._click_by_text(r"mi negocio", required=False)
            self._click_by_text(r"administrar negocios")

            info_general = self.page.get_by_text(re.compile(r"informaci[oó]n general", re.IGNORECASE))
            detalles = self.page.get_by_text(re.compile(r"detalles de la cuenta", re.IGNORECASE))
            negocios = self.page.get_by_text(re.compile(r"tus negocios", re.IGNORECASE))
            legal = self.page.get_by_text(re.compile(r"secci[oó]n legal", re.IGNORECASE))

            validations = [
                self._is_visible(info_general),
                self._is_visible(detalles),
                self._is_visible(negocios),
                self._is_visible(legal),
            ]

            screenshots.append(self._capture("04_administrar_negocios_full_page", full_page=True))
            passed = all(validations)
            details = "Administrar Negocios sections are visible." if passed else "Missing one or more account sections."
            self._mark_result(key, passed, details, screenshots=screenshots)
        except Exception as exc:  # noqa: BLE001
            screenshots.append(self._capture("04_administrar_negocios_failure", full_page=True))
            self._mark_result(key, False, f"Could not open Administrar Negocios view: {exc}", screenshots=screenshots)

    def _step_validate_informacion_general(self) -> None:
        key = "Información General"
        try:
            user_name_visible = self._first_visible(
                [
                    self.page.get_by_text(re.compile(r"\bnombre\b", re.IGNORECASE)),
                    self.page.locator("[data-testid*='name']"),
                ],
                timeout_per_locator=2_000,
            )
            user_email_visible = self._first_visible(
                [
                    self.page.get_by_text(re.compile(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", re.IGNORECASE)),
                    self.page.get_by_text(re.compile(r"\bemail\b", re.IGNORECASE)),
                ],
                timeout_per_locator=3_000,
            )
            business_plan = self._is_visible(self.page.get_by_text(re.compile(r"business plan", re.IGNORECASE)))
            cambiar_plan = self._is_visible(self.page.get_by_role("button", name=re.compile(r"cambiar plan", re.IGNORECASE)))

            passed = bool(user_name_visible and user_email_visible and business_plan and cambiar_plan)
            details = "Informacion General content is visible." if passed else "Missing one or more Informacion General validations."
            self._mark_result(key, passed, details)
        except Exception as exc:  # noqa: BLE001
            self._mark_result(key, False, f"Informacion General validation failed: {exc}")

    def _step_validate_detalles_cuenta(self) -> None:
        key = "Detalles de la Cuenta"
        try:
            cuenta_creada = self._is_visible(self.page.get_by_text(re.compile(r"cuenta creada", re.IGNORECASE)))
            estado_activo = self._is_visible(self.page.get_by_text(re.compile(r"estado activo", re.IGNORECASE)))
            idioma = self._is_visible(self.page.get_by_text(re.compile(r"idioma seleccionado", re.IGNORECASE)))

            passed = cuenta_creada and estado_activo and idioma
            details = "Detalles de la Cuenta validated." if passed else "Missing one or more Detalles de la Cuenta labels."
            self._mark_result(key, passed, details)
        except Exception as exc:  # noqa: BLE001
            self._mark_result(key, False, f"Detalles de la Cuenta validation failed: {exc}")

    def _step_validate_tus_negocios(self) -> None:
        key = "Tus Negocios"
        try:
            business_list = self._is_visible(self.page.get_by_text(re.compile(r"tus negocios", re.IGNORECASE)))
            add_button = self._is_visible(self.page.get_by_role("button", name=re.compile(r"agregar negocio", re.IGNORECASE)))
            quota_text = self._is_visible(self.page.get_by_text(re.compile(r"tienes\s*2\s*de\s*3\s*negocios", re.IGNORECASE)))

            passed = business_list and add_button and quota_text
            details = "Tus Negocios section validated." if passed else "Missing one or more Tus Negocios validations."
            self._mark_result(key, passed, details)
        except Exception as exc:  # noqa: BLE001
            self._mark_result(key, False, f"Tus Negocios validation failed: {exc}")

    def _validate_legal_link(self, link_text_regex: str, heading_regex: str, key: str, screenshot_name: str) -> None:
        screenshots: List[str] = []
        target_page: Optional[Page] = None
        opened_new_tab = False
        try:
            link = self._first_visible(self._by_text_locators(link_text_regex), timeout_per_locator=3_500)
            if not link:
                raise RuntimeError(f"Could not find legal link /{link_text_regex}/i")

            pages_before = list(self.context.pages)
            self._click_and_wait(link)
            self.page.wait_for_timeout(1_500)
            pages_after = list(self.context.pages)

            if len(pages_after) > len(pages_before):
                target_page = pages_after[-1]
                opened_new_tab = True
                target_page.bring_to_front()
                self._wait_for_ui(target_page)
            else:
                target_page = self.page

            heading_visible = self._is_visible(
                target_page.get_by_role("heading", name=re.compile(heading_regex, re.IGNORECASE)),
                timeout_ms=6_000,
            ) or self._is_visible(target_page.get_by_text(re.compile(heading_regex, re.IGNORECASE)), timeout_ms=6_000)

            body_text = target_page.locator("main, article, body").first.inner_text(timeout=8_000)
            legal_content_visible = len(" ".join(body_text.split())) > 120

            screenshots.append(self._capture(screenshot_name, page=target_page, full_page=True))
            final_url = target_page.url

            passed = heading_visible and legal_content_visible
            details = "Legal page heading and content were validated." if passed else "Heading or legal content was not validated."
            self._mark_result(key, passed, details, screenshots=screenshots, final_url=final_url)
        except Exception as exc:  # noqa: BLE001
            if target_page:
                screenshots.append(self._capture(f"{screenshot_name}_failure", page=target_page, full_page=True))
            else:
                screenshots.append(self._capture(f"{screenshot_name}_failure", full_page=True))
            self._mark_result(key, False, f"Legal link validation failed: {exc}", screenshots=screenshots)
        finally:
            # Cleanup: return to app tab regardless of whether legal page was same-tab or new tab.
            try:
                if opened_new_tab and target_page and not target_page.is_closed():
                    target_page.close()
                    self.page.bring_to_front()
                    self._wait_for_ui(self.page)
                elif not opened_new_tab and self.page.url != "about:blank":
                    self.page.go_back(wait_until="domcontentloaded", timeout=8_000)
                    self._wait_for_ui(self.page)
            except Exception:  # noqa: BLE001
                pass

    def _step_validate_terminos(self) -> None:
        self._validate_legal_link(
            link_text_regex=r"t[eé]rminos y condiciones",
            heading_regex=r"t[eé]rminos y condiciones",
            key="Términos y Condiciones",
            screenshot_name="08_terminos_y_condiciones",
        )

    def _step_validate_politica(self) -> None:
        self._validate_legal_link(
            link_text_regex=r"pol[ií]tica de privacidad",
            heading_regex=r"pol[ií]tica de privacidad",
            key="Política de Privacidad",
            screenshot_name="09_politica_de_privacidad",
        )


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def write_reports(
    output_dir: Path,
    start_time: str,
    end_time: str,
    base_url: str,
    results: Dict[str, StepResult],
) -> int:
    summary = {key: value.status for key, value in results.items()}
    failures = [key for key, value in results.items() if value.status != "PASS"]

    json_report = {
        "name": TEST_NAME,
        "started_at_utc": start_time,
        "finished_at_utc": end_time,
        "application_url": base_url,
        "summary": summary,
        "results": {
            key: {
                "status": value.status,
                "details": value.details,
                "screenshots": value.screenshots,
                "final_url": value.final_url,
            }
            for key, value in results.items()
        },
        "overall_status": "PASS" if not failures else "FAIL",
        "failures": failures,
    }

    json_path = output_dir / "final_report.json"
    json_path.write_text(json.dumps(json_report, indent=2, ensure_ascii=False), encoding="utf-8")

    md_lines = [
        f"# {TEST_NAME}",
        "",
        f"- Started (UTC): {start_time}",
        f"- Finished (UTC): {end_time}",
        f"- Application URL: {base_url}",
        f"- Overall: **{json_report['overall_status']}**",
        "",
        "## Step Results",
        "",
        "| Field | Status | Details |",
        "|---|---|---|",
    ]
    for key in REPORT_FIELDS:
        result = results[key]
        md_lines.append(f"| {key} | {result.status} | {result.details} |")

    md_lines.extend(["", "## Evidence", ""])
    for key in REPORT_FIELDS:
        result = results[key]
        if result.screenshots or result.final_url:
            md_lines.append(f"### {key}")
            for shot in result.screenshots:
                md_lines.append(f"- Screenshot: `{shot}`")
            if result.final_url:
                md_lines.append(f"- Final URL: `{result.final_url}`")
            md_lines.append("")

    md_path = output_dir / "final_report.md"
    md_path.write_text("\n".join(md_lines), encoding="utf-8")

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"\nJSON report: {json_path}")
    print(f"Markdown report: {md_path}")
    return 0 if not failures else 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run SaleADS Mi Negocio full workflow test with environment-agnostic selectors."
    )
    parser.add_argument(
        "--url",
        required=True,
        help="SaleADS login URL for the target environment (dev/staging/prod).",
    )
    parser.add_argument(
        "--account-email",
        default=DEFAULT_ACCOUNT,
        help="Google account email to choose if account selector appears.",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run browser in headed mode.",
    )
    parser.add_argument(
        "--slow-mo-ms",
        type=int,
        default=150,
        help="Slow down Playwright actions in milliseconds.",
    )
    parser.add_argument(
        "--timeout-ms",
        type=int,
        default=25_000,
        help="Action timeout per Playwright call in milliseconds.",
    )
    parser.add_argument(
        "--output-dir",
        default="automation/artifacts/saleads_mi_negocio_full_test",
        help="Directory where screenshots and final reports are saved.",
    )
    return parser.parse_args()


def build_output_dir(base_output_dir: str) -> Path:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    output_dir = Path(base_output_dir) / timestamp
    output_dir.mkdir(parents=True, exist_ok=True)
    return output_dir


def run(playwright: Playwright, args: argparse.Namespace) -> int:
    output_dir = build_output_dir(args.output_dir)
    start_time = utc_now()

    browser = playwright.chromium.launch(headless=not args.headed, slow_mo=args.slow_mo_ms)
    context = browser.new_context(ignore_https_errors=True)
    page = context.new_page()
    page.set_default_timeout(args.timeout_ms)
    page.goto(args.url, wait_until="domcontentloaded")

    workflow = SaleadsMiNegocioWorkflow(
        page=page,
        context=context,
        output_dir=output_dir,
        account_email=args.account_email,
        action_timeout_ms=args.timeout_ms,
    )

    try:
        results = workflow.run()
    finally:
        context.close()
        browser.close()

    end_time = utc_now()
    return write_reports(
        output_dir=output_dir,
        start_time=start_time,
        end_time=end_time,
        base_url=args.url,
        results=results,
    )


def main() -> int:
    args = parse_args()
    with sync_playwright() as playwright:
        return run(playwright, args)


if __name__ == "__main__":
    sys.exit(main())
