# SaleADS Mi Negocio workflow automation

This folder contains a standalone Playwright script for validating the **SaleADS Mi Negocio** end-to-end workflow in any environment (dev/staging/production), without hardcoding a domain.

## Script

- `saleads_mi_negocio_full_test.py`

## What it validates

The script executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It also:

- Waits for UI load after clicks/navigation.
- Prefers visible text selectors.
- Handles legal links that open in same tab or new tab.
- Returns to the application tab after legal checks.
- Captures screenshots at key checkpoints.
- Writes a machine-readable `report.json`.

## Setup

Install dependencies:

```bash
pip install playwright
python3 -m playwright install chromium
```

## Run

Pass the login URL explicitly:

```bash
python3 automation/saleads_mi_negocio_full_test.py --headless --url "https://<your-saleads-login-url>"
```

Or set it via environment variable:

```bash
export SALEADS_URL="https://<your-saleads-login-url>"
python3 automation/saleads_mi_negocio_full_test.py --headless
```

Artifacts are created under:

- `artifacts/saleads-mi-negocio/<timestamp>/`
  - checkpoint screenshots
  - `report.json`

The process exits with:

- `0` when all validations pass
- `1` when one or more validations fail
- `2` when the target URL is missing
