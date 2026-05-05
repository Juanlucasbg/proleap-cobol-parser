# SaleADS Mi Negocio full workflow test

This directory contains an automated end-to-end Playwright test script for:

- Login with Google
- Mi Negocio navigation workflow
- Agregar Negocio modal validation
- Administrar Negocios page section validations
- Legal links validation (Términos y Condiciones / Política de Privacidad)
- Screenshot evidence and final JSON report output

## Why this script is environment-agnostic

- No SaleADS domain is hardcoded.
- The login URL is passed at runtime (`--url`) so it works for dev/staging/prod.
- Primary interactions use visible text selectors (Spanish/English variants where needed).

## Prerequisites

- Python 3.12+
- Playwright for Python
- Chromium browser binaries installed by Playwright

## Setup

```bash
python3 -m venv automation/.venv
automation/.venv/bin/pip install -r automation/saleads/requirements.txt
automation/.venv/bin/python -m playwright install chromium
```

## Run

```bash
automation/.venv/bin/python automation/saleads/mi_negocio_workflow_test.py \
  --url "https://<current-saleads-environment>/login"
```

Optional flags:

- `--headless` run without opening visible browser window
- `--email` Google account email to select if account chooser is shown
- `--output-dir` custom artifacts directory

Example:

```bash
automation/.venv/bin/python automation/saleads/mi_negocio_workflow_test.py \
  --url "https://staging.example.com/login" \
  --email "juanlucasbarbiergarzon@gmail.com" \
  --output-dir "automation/saleads/artifacts/run-$(date +%Y%m%d-%H%M%S)"
```

## Artifacts

By default:

- Screenshots: `automation/saleads/artifacts/latest/screenshots/*.png`
- Final report: `automation/saleads/artifacts/latest/report.json`

The final report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
