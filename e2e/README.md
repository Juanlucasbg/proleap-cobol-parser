# SaleADS Mi Negocio Full Workflow Test

This folder contains an end-to-end browser automation script for the workflow:

- Login with Google
- Navigate to **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Open and validate **Administrar Negocios**
- Validate:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
- Validate legal links:
  - Términos y Condiciones
  - Política de Privacidad

The script is environment-agnostic and does **not** hardcode a SaleADS URL.

## Prerequisites

- Python 3.10+
- Playwright browser binaries installed:
  - `python3 -m playwright install chromium`

## Install

```bash
pip3 install -r e2e/requirements.txt
python3 -m playwright install chromium
```

## Run

```bash
python3 e2e/saleads_mi_negocio_full_test.py
```

### Optional environment variables

- `SALEADS_START_URL`:
  - Optional initial URL if running unattended.
  - If omitted, script opens a browser and expects the app/login page to be loaded manually.
- `SALEADS_HEADLESS=true|false` (default: `false`)
- `SALEADS_TIMEOUT_MS` (default: `15000`)
- `SALEADS_SCREENSHOT_DIR` (default: `e2e/artifacts/screenshots`)
- `SALEADS_REPORT_PATH` (default: `e2e/artifacts/saleads_mi_negocio_report.json`)

## Outputs

- Screenshots at major checkpoints and on failures
- JSON report with per-step PASS/FAIL:
  - `e2e/artifacts/saleads_mi_negocio_report.json`

