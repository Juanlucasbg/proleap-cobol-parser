# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic UI workflow test for SaleADS:

- Login with Google
- Navigate to **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Validate **Administrar Negocios** sections
- Validate **Términos y Condiciones** and **Política de Privacidad**
- Capture screenshots and final PASS/FAIL report

## Why this is environment-agnostic

The script does not hardcode a SaleADS domain.

You can run it in two ways:

1. Attach to an already open Chromium browser on a SaleADS login page via CDP.
2. Launch a fresh browser and pass the login URL with `--saleads-url`.

## Setup

```bash
python3 -m venv saleads_e2e/.venv
source saleads_e2e/.venv/bin/activate
python -m pip install -r saleads_e2e/requirements.txt
python -m playwright install chromium
```

## Run

### Option A: Attach to existing browser/session (recommended if already logged-in context exists)

```bash
python saleads_e2e/saleads_mi_negocio_full_test.py \
  --cdp-url "http://127.0.0.1:9222"
```

### Option B: Launch browser and open a runtime-provided SaleADS URL

```bash
python saleads_e2e/saleads_mi_negocio_full_test.py \
  --saleads-url "https://<current-saleads-environment>/login"
```

Optional:

- `--headless` (for non-CDP launch mode)
- `--artifacts-dir` to customize output location

## Artifacts

By default, all evidence is stored in:

- `saleads_e2e/artifacts/screenshots_<timestamp>/*.png`
- `saleads_e2e/artifacts/report_<timestamp>.json`

The report includes PASS/FAIL status for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
