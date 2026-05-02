# SaleADS Mi Negocio Full Workflow Test

This repository now includes an automated browser workflow for validating the
SaleADS "Mi Negocio" module end-to-end, including login, menu flows, legal-link
handling (same tab/new tab), screenshots, and a PASS/FAIL report.

## Files

- `scripts/saleads_mi_negocio_full_test.py`
- `scripts/requirements-saleads-e2e.txt`

## Why this works across environments

- The script does **not** hardcode a SaleADS domain.
- It accepts `--base-url` (or `SALEADS_BASE_URL`) for the current environment.
- The start URL is required so execution always begins on the active environment's login page.

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r scripts/requirements-saleads-e2e.txt
python -m playwright install chromium
```

## Run

### Option A: Provide active environment login URL

```bash
python scripts/saleads_mi_negocio_full_test.py \
  --base-url "https://<current-saleads-env>/login"
```

### Option B: Use environment variable

```bash
export SALEADS_BASE_URL="https://<current-saleads-env>/login"
python scripts/saleads_mi_negocio_full_test.py
```

> Note: A base URL is required for this standalone runner. This ensures the script
> starts on the correct login page in the active SaleADS environment.

### Optional flags

- `--headless` (run browser without UI)
- `--slow-mo-ms 500` (increase interaction delay)
- `--output-dir artifacts/custom-run-1` (custom report/screenshot path)

## Evidence and report outputs

Each run writes:

- `report.json`: structured per-step result and final summary
- `report.md`: human-readable final report
- `screenshots/*.png`: checkpoint screenshots, including:
  - dashboard after login
  - expanded Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios page
  - Términos y Condiciones page
  - Política de Privacidad page

By default these are created under:

`artifacts/saleads-mi-negocio-<timestamp>/`

## Final report fields

The report includes explicit PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
