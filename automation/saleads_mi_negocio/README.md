# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic end-to-end workflow test:

- Script: `full_workflow_test.py`
- Test name: `saleads_mi_negocio_full_test`

## What it validates

The script executes the requested flow:

1. Login with Google (and account pick when selector appears).
2. Open **Negocio > Mi Negocio** and validate submenu items.
3. Open and validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate account sections.
5. Validate **Informacion General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Terminos y Condiciones** (including new-tab behavior).
9. Validate **Politica de Privacidad** (including new-tab behavior).
10. Emit final PASS/FAIL report by requested fields.

The script captures screenshots at important checkpoints and writes a JSON report
with per-step validations and evidence paths.

## Prerequisites

Python 3.10+ and Playwright for Python:

```bash
pip3 install playwright
python3 -m playwright install chromium
```

## How to run

Use one of these options:

### Option A: Start from URL

```bash
SALEADS_URL="https://<current-env-saleads-login-url>" python3 automation/saleads_mi_negocio/full_workflow_test.py
```

### Option B: Attach to existing browser session

If the browser is already open on SaleADS login page:

```bash
CHROME_CDP_URL="http://127.0.0.1:9222" python3 automation/saleads_mi_negocio/full_workflow_test.py
```

## Optional env vars

- `HEADLESS=true|false` (default: `false` when launching new browser)
- `SALEADS_URL` for fresh launch mode
- `CHROME_CDP_URL` for attach mode

## Artifacts

Outputs are stored under:

```text
automation/saleads_mi_negocio/artifacts/<timestamp>/
```

Including:

- step screenshots (`.png`)
- `final_report.json` with full PASS/FAIL details and final URLs for legal pages
