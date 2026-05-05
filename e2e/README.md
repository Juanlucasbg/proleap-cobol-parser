# SaleADS Mi Negocio Full Workflow Test

This folder contains an end-to-end automated test that validates the complete
Mi Negocio flow requested in the `saleads_mi_negocio_full_test` scenario.

The test is environment-agnostic:

- It does not hardcode a SaleADS domain.
- It receives the login URL from an environment variable.
- It uses visible text-first selectors with fallback locators.

## What this test validates

1. Login with Google (and continue after login).
2. Sidebar navigation is visible.
3. Expand `Negocio` -> `Mi Negocio` and validate menu entries.
4. Open and validate `Agregar Negocio` modal.
5. Open `Administrar Negocios` view and validate sections:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
   - `Sección Legal`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Capture screenshots at important checkpoints.
8. Emit a PASS/FAIL report by validation step.

## Prerequisites

- Python 3.10+ (3.12 recommended)
- pip

## Install

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r e2e/requirements.txt
python -m playwright install chromium
```

## Run

```bash
export SALEADS_LOGIN_URL="https://<your-current-saleads-environment>/login"
export SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
pytest -s e2e/tests/test_saleads_mi_negocio_full.py
```

Optional:

```bash
export SALEADS_HEADLESS=false
export SALEADS_SLOW_MO_MS=150
```

## Output evidence and report

- Screenshots: `e2e/artifacts/screenshots/`
- JSON report: `e2e/artifacts/saleads_mi_negocio_full_report.json`

The report contains PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
