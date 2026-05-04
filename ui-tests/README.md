# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic end-to-end automation for validating the full "Mi Negocio" workflow in SaleADS.ai.

## What it validates

The script executes and reports PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad

It also captures screenshots at the required checkpoints and stores legal page final URLs.

## Environment-agnostic behavior

- The test **does not hardcode any domain**.
- You provide the start login URL for the current environment (dev/staging/prod) using `--start-url` or `SALEADS_START_URL`.
- Selectors are based primarily on **visible text** and role-based lookups.

## Setup

```bash
pip3 install -r ui-tests/requirements.txt
python3 -m playwright install chromium
```

## Run

```bash
python3 ui-tests/saleads_mi_negocio_full_test.py \
  --start-url "https://<current-saleads-login-url>" \
  --headless true
```

Or with environment variables:

```bash
export SALEADS_START_URL="https://<current-saleads-login-url>"
export SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com"
export SALEADS_HEADLESS="true"
python3 ui-tests/saleads_mi_negocio_full_test.py
```

## Outputs

Artifacts are written to `ui-tests/artifacts` by default:

- `01_dashboard_loaded.png`
- `02_mi_negocio_menu_expanded.png`
- `03_agregar_negocio_modal.png`
- `04_administrar_negocios_full.png`
- `05_terminos_y_condiciones.png`
- `06_politica_de_privacidad.png`
- `final-report.json`

Override output folder:

```bash
python3 ui-tests/saleads_mi_negocio_full_test.py \
  --start-url "https://<current-saleads-login-url>" \
  --artifacts-dir "ui-tests/artifacts-run-001"
```
