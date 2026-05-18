#!/usr/bin/env python3
"""
End-to-end validation for SaleADS "Mi Negocio" workflow.

This script is environment-agnostic:
- It first tries to attach to an existing Chromium tab via CDP.
- If CDP is unavailable, it can launch a new browser using a provided base URL.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from playwright.sync_api import Browser, Page, TimeoutError as PlaywrightTimeoutError, sync_playwright


REPORT_FIELDS = [
    "Login",
    "Mi Negocio menu",
    "Agregar Negocio modal",
    "Administrar Negocios view",
    "Informacion General",
    "Detalles de la Cuenta",
    "Tus Negocios",
    "Terminos y Condiciones",
    "Politica de Privacidad",
]


@dataclass
class StepResult:
    name: str
    status: str = "FAIL"
    checks: list[dict[str, Any]] = field(default_factory=list)
    evidence: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    def check(self, label: str, condition: bool, details: str | None = None) -> bool:
        self.checks.append({"label": label, "pass": condition, "details": details})
        return condition

    def finalize(self) -> None:
        self.status = "PASS" if self.checks and all(item["pass"] for item in self.checks) else "FAIL"


def now_utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def safe_slug(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def wait_for_ui(page: Page, pause_ms: int = 900) -> None:
    try:
        page.wait_for_load_state("networkidle", timeout=12_000)
    except Exception:
        # Some apps keep active network traffic. We still pause briefly.
        pass
    page.wait_for_timeout(pause_ms)


def screenshot(page: Page, out_dir: Path, name: str, full_page: bool = False) -> str:
    filename = f"{safe_slug(name)}.png"
    path = out_dir / filename
    page.screenshot(path=str(path), full_page=full_page)
    return str(path)


def first_visible_locator(page: Page, text: str):
    escaped = re.escape(text)
    exact = re.compile(rf"^{escaped}$", re.IGNORECASE)
    contains = re.compile(escaped, re.IGNORECASE)

    candidates = [
        page.get_by_role("button", name=exact),
        page.get_by_role("link", name=exact),
        page.get_by_role("menuitem", name=exact),
        page.get_by_role("tab", name=exact),
        page.get_by_text(exact),
        page.get_by_text(contains),
    ]

    for locator in candidates:
        try:
            count = locator.count()
            for idx in range(count):
                item = locator.nth(idx)
                if item.is_visible():
                    return item
        except Exception:
            continue

    return None


def click_visible_text(page: Page, labels: list[str], required: bool = True) -> str | None:
    for label in labels:
        locator = first_visible_locator(page, label)
        if locator is None:
            continue
        locator.scroll_into_view_if_needed()
        locator.click()
        wait_for_ui(page)
        return label

    if required:
        raise AssertionError(f"No clickable element found for labels: {labels}")

    return None


def visible_text(page: Page, text: str) -> bool:
    locator = first_visible_locator(page, text)
    return locator is not None


def visible_any(page: Page, labels: list[str]) -> bool:
    return any(visible_text(page, label) for label in labels)


def choose_google_account_if_prompted(page: Page, email: str) -> bool:
    if not visible_text(page, email):
        return False
    clicked = click_visible_text(page, [email], required=False)
    return clicked is not None


def click_maybe_new_tab(page: Page, labels: list[str]) -> tuple[Page, bool]:
    try:
        with page.context.expect_page(timeout=5_000) as page_info:
            click_visible_text(page, labels, required=True)
        new_page = page_info.value
        wait_for_ui(new_page)
        return new_page, True
    except PlaywrightTimeoutError:
        click_visible_text(page, labels, required=True)
        return page, False


def attach_existing_page(playwright, cdp_urls: list[str]) -> tuple[Browser | None, Page | None]:
    for cdp_url in cdp_urls:
        try:
            browser = playwright.chromium.connect_over_cdp(cdp_url, timeout=5_000)
        except Exception:
            continue

        for context in browser.contexts:
            pages = context.pages
            if pages:
                return browser, pages[-1]
        browser.close()
    return None, None


def run_test(base_url: str | None, cdp_urls: list[str], headless: bool, artifacts_root: Path) -> dict[str, Any]:
    results: dict[str, StepResult] = {field: StepResult(field) for field in REPORT_FIELDS}
    urls: dict[str, str] = {}
    metadata: dict[str, Any] = {
        "started_at": now_utc(),
        "base_url": base_url,
        "cdp_urls": cdp_urls,
        "mode": None,
    }

    run_dir = artifacts_root / f"run_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    run_dir.mkdir(parents=True, exist_ok=True)

    browser_to_close: Browser | None = None
    app_page: Page | None = None

    with sync_playwright() as playwright:
        browser, page = attach_existing_page(playwright, cdp_urls)
        if page is not None:
            metadata["mode"] = "attached_cdp"
            app_page = page
            browser_to_close = browser
        else:
            if not base_url:
                raise RuntimeError(
                    "Unable to attach to an existing browser tab and no base URL was provided. "
                    "Set SALEADS_LOGIN_URL or pass --base-url."
                )
            metadata["mode"] = "launched_browser"
            browser_to_close = playwright.chromium.launch(headless=headless)
            context = browser_to_close.new_context()
            app_page = context.new_page()
            app_page.goto(base_url, wait_until="domcontentloaded", timeout=30_000)
            wait_for_ui(app_page)

        assert app_page is not None
        page = app_page

        # Step 1: Login with Google
        step = results["Login"]
        try:
            click_visible_text(
                page,
                [
                    "Sign in with Google",
                    "Iniciar sesion con Google",
                    "Iniciar sesion",
                    "Login with Google",
                    "Google",
                ],
                required=False,
            )
            choose_google_account_if_prompted(page, "juanlucasbarbiergarzon@gmail.com")

            main_interface_ok = visible_any(page, ["Dashboard", "Panel", "Negocio", "Mi Negocio"])
            sidebar_ok = page.get_by_role("navigation").first.is_visible() or visible_any(page, ["Negocio", "Mi Negocio"])

            step.check("Main application interface appears", main_interface_ok)
            step.check("Left sidebar is visible", sidebar_ok)
            step.evidence.append(screenshot(page, run_dir, "01_dashboard_loaded"))
        except Exception as exc:
            step.notes.append(str(exc))
        step.finalize()

        # Step 2: Open Mi Negocio menu
        step = results["Mi Negocio menu"]
        try:
            click_visible_text(page, ["Negocio"], required=False)
            click_visible_text(page, ["Mi Negocio"], required=True)
            submenu_expanded = visible_any(page, ["Agregar Negocio", "Administrar Negocios"])
            agregar_visible = visible_text(page, "Agregar Negocio")
            administrar_visible = visible_text(page, "Administrar Negocios")

            step.check("Submenu expands", submenu_expanded)
            step.check("Agregar Negocio visible", agregar_visible)
            step.check("Administrar Negocios visible", administrar_visible)
            step.evidence.append(screenshot(page, run_dir, "02_mi_negocio_menu_expanded"))
        except Exception as exc:
            step.notes.append(str(exc))
        step.finalize()

        # Step 3: Validate Agregar Negocio modal
        step = results["Agregar Negocio modal"]
        try:
            click_visible_text(page, ["Agregar Negocio"], required=True)

            title_ok = visible_any(page, ["Crear Nuevo Negocio"])
            input_ok = visible_any(page, ["Nombre del Negocio"])
            limit_ok = visible_any(page, ["Tienes 2 de 3 negocios"])
            cancel_ok = visible_any(page, ["Cancelar"])
            create_ok = visible_any(page, ["Crear Negocio"])

            step.check("Modal title visible", title_ok)
            step.check("Nombre del Negocio input exists", input_ok)
            step.check("Business limit text visible", limit_ok)
            step.check("Cancelar button present", cancel_ok)
            step.check("Crear Negocio button present", create_ok)

            name_input = first_visible_locator(page, "Nombre del Negocio")
            if name_input is not None:
                try:
                    name_input.fill("Negocio Prueba Automatizacion")
                    wait_for_ui(page)
                except Exception:
                    pass
            click_visible_text(page, ["Cancelar"], required=False)

            step.evidence.append(screenshot(page, run_dir, "03_agregar_negocio_modal"))
        except Exception as exc:
            step.notes.append(str(exc))
        step.finalize()

        # Step 4: Open Administrar Negocios
        step = results["Administrar Negocios view"]
        try:
            if not visible_text(page, "Administrar Negocios"):
                click_visible_text(page, ["Mi Negocio"], required=False)
            click_visible_text(page, ["Administrar Negocios"], required=True)

            info_general_ok = visible_any(page, ["Informacion General", "Información General"])
            detalles_ok = visible_any(page, ["Detalles de la Cuenta"])
            negocios_ok = visible_any(page, ["Tus Negocios"])
            legal_ok = visible_any(page, ["Seccion Legal", "Sección Legal", "Legal"])

            step.check("Informacion General section exists", info_general_ok)
            step.check("Detalles de la Cuenta section exists", detalles_ok)
            step.check("Tus Negocios section exists", negocios_ok)
            step.check("Seccion Legal section exists", legal_ok)
            step.evidence.append(screenshot(page, run_dir, "04_administrar_negocios_page", full_page=True))
        except Exception as exc:
            step.notes.append(str(exc))
        step.finalize()

        # Step 5: Validate Informacion General
        step = results["Informacion General"]
        try:
            step.check("User name visible", visible_any(page, ["@", "Nombre"]))
            step.check("User email visible", visible_any(page, ["@", ".com"]))
            step.check("BUSINESS PLAN text visible", visible_any(page, ["BUSINESS PLAN"]))
            step.check("Cambiar Plan button visible", visible_any(page, ["Cambiar Plan"]))
        except Exception as exc:
            step.notes.append(str(exc))
        step.finalize()

        # Step 6: Validate Detalles de la Cuenta
        step = results["Detalles de la Cuenta"]
        try:
            step.check("Cuenta creada visible", visible_any(page, ["Cuenta creada"]))
            step.check("Estado activo visible", visible_any(page, ["Estado activo"]))
            step.check("Idioma seleccionado visible", visible_any(page, ["Idioma seleccionado"]))
        except Exception as exc:
            step.notes.append(str(exc))
        step.finalize()

        # Step 7: Validate Tus Negocios
        step = results["Tus Negocios"]
        try:
            step.check("Business list visible", visible_any(page, ["Tus Negocios"]))
            step.check("Agregar Negocio button exists", visible_any(page, ["Agregar Negocio"]))
            step.check("Business limit text visible", visible_any(page, ["Tienes 2 de 3 negocios"]))
        except Exception as exc:
            step.notes.append(str(exc))
        step.finalize()

        # Step 8: Validate Terminos y Condiciones
        step = results["Terminos y Condiciones"]
        legal_page = page
        opened_new_tab = False
        try:
            legal_page, opened_new_tab = click_maybe_new_tab(page, ["Terminos y Condiciones", "Términos y Condiciones"])
            heading_ok = visible_any(legal_page, ["Terminos y Condiciones", "Términos y Condiciones"])
            body_ok = len(legal_page.locator("p, article, main").all_inner_texts()) > 0

            step.check("Heading visible", heading_ok)
            step.check("Legal content visible", body_ok)
            step.evidence.append(screenshot(legal_page, run_dir, "08_terminos_y_condiciones"))
            urls["Terminos y Condiciones"] = legal_page.url
        except Exception as exc:
            step.notes.append(str(exc))
        finally:
            try:
                if opened_new_tab:
                    legal_page.close()
                    page.bring_to_front()
                    wait_for_ui(page)
            except Exception:
                pass
        step.finalize()

        # Step 9: Validate Politica de Privacidad
        step = results["Politica de Privacidad"]
        privacy_page = page
        opened_new_tab = False
        try:
            privacy_page, opened_new_tab = click_maybe_new_tab(page, ["Politica de Privacidad", "Política de Privacidad"])
            heading_ok = visible_any(privacy_page, ["Politica de Privacidad", "Política de Privacidad"])
            body_ok = len(privacy_page.locator("p, article, main").all_inner_texts()) > 0

            step.check("Heading visible", heading_ok)
            step.check("Legal content visible", body_ok)
            step.evidence.append(screenshot(privacy_page, run_dir, "09_politica_de_privacidad"))
            urls["Politica de Privacidad"] = privacy_page.url
        except Exception as exc:
            step.notes.append(str(exc))
        finally:
            try:
                if opened_new_tab:
                    privacy_page.close()
                    page.bring_to_front()
                    wait_for_ui(page)
            except Exception:
                pass
        step.finalize()

        if browser_to_close is not None and metadata["mode"] == "launched_browser":
            browser_to_close.close()

    report = {
        "name": "saleads_mi_negocio_full_test",
        "generated_at": now_utc(),
        "metadata": metadata,
        "results": {
            key: {
                "status": value.status,
                "checks": value.checks,
                "evidence": value.evidence,
                "notes": value.notes,
            }
            for key, value in results.items()
        },
        "final_urls": urls,
        "artifacts_dir": str(run_dir),
    }

    return report


def print_summary(report: dict[str, Any]) -> None:
    print("\nFinal Report (PASS/FAIL):")
    print("-" * 60)
    for field in REPORT_FIELDS:
        status = report["results"][field]["status"]
        print(f"{field:28} {status}")
    print("-" * 60)
    for key, value in report.get("final_urls", {}).items():
        print(f"{key} URL: {value}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio end-to-end workflow test.")
    parser.add_argument("--base-url", default=os.getenv("SALEADS_LOGIN_URL"), help="Environment login URL.")
    parser.add_argument(
        "--cdp-url",
        action="append",
        default=[],
        help="Chromium CDP endpoint. Can be provided multiple times.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        default=os.getenv("SALEADS_HEADLESS", "true").lower() in {"1", "true", "yes"},
        help="Launch browser in headless mode when using --base-url.",
    )
    parser.add_argument(
        "--artifacts-dir",
        default="/workspace/automation/artifacts",
        help="Directory to store screenshots and report files.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    cdp_urls = args.cdp_url or os.getenv("SALEADS_CDP_URL", "http://127.0.0.1:9222").split(",")
    artifacts_dir = Path(args.artifacts_dir)
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    try:
        report = run_test(
            base_url=args.base_url,
            cdp_urls=[url.strip() for url in cdp_urls if url.strip()],
            headless=args.headless,
            artifacts_root=artifacts_dir,
        )
    except Exception as exc:
        fallback_report = {
            "name": "saleads_mi_negocio_full_test",
            "generated_at": now_utc(),
            "error": str(exc),
            "results": {field: {"status": "FAIL"} for field in REPORT_FIELDS},
        }
        output_file = artifacts_dir / "saleads_mi_negocio_full_test_report.json"
        output_file.write_text(json.dumps(fallback_report, indent=2), encoding="utf-8")
        print(json.dumps(fallback_report, indent=2))
        return 1

    output_file = artifacts_dir / "saleads_mi_negocio_full_test_report.json"
    output_file.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print_summary(report)
    print(f"\nReport file: {output_file}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
