# SaleADS Mi Negocio full workflow test

This directory contains the automation script for:

- `saleads_mi_negocio_full_test`

The script validates the complete **Mi Negocio** workflow after Google login, captures screenshots at required checkpoints, and produces a final PASS/FAIL report per requested section.

## Files

- `saleads_mi_negocio_full_test.py`: main Playwright automation runner
- `requirements.txt`: Python dependencies

## Prerequisites

- Python 3.10+ (recommended)
- Browser binaries for Playwright

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m playwright install chromium
```

## Run

```bash
python saleads_mi_negocio_full_test.py --login-url "https://<current-saleads-environment>/login"
```

You can also provide the URL via env var:

```bash
export SALEADS_LOGIN_URL="https://<current-saleads-environment>/login"
python saleads_mi_negocio_full_test.py
```

Optional flags:

- `--headed`: run with visible browser window
- `--slow-mo 200`: delay between actions in milliseconds
- `--timeout-ms 15000`: default action timeout
- `--output-dir artifacts/<name>`: where report/screenshots are saved

## Artifacts

The run generates:

- `artifacts/<timestamp>/final_report.json`
- `artifacts/<timestamp>/screenshots/*.png`

The report includes:

- Overall status
- PASS/FAIL per each required field:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- Final legal URLs and whether legal links opened in a new tab
