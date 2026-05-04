# SaleADS Mi Negocio Full Workflow Automation

This folder contains an environment-agnostic UI automation script for:

- Login with Google
- Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios page and section validations
- Legal links validation (including new-tab handling)
- Screenshot evidence and JSON report generation

## File

- `tests/saleads_mi_negocio_full_test.py`

## Requirements

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m playwright install chromium
```

## Run modes

The script supports two execution modes so it can run in any SaleADS environment without hardcoding a domain.

### 1) Start URL mode

Use when the automation should open the login page itself:

```bash
python tests/saleads_mi_negocio_full_test.py --start-url "https://<current-saleads-env>/login"
```

You can also provide environment variables:

```bash
export SALEADS_START_URL="https://<current-saleads-env>/login"
python tests/saleads_mi_negocio_full_test.py
```

### 2) Existing browser mode (CDP)

Use when a browser is already open on the SaleADS login page (as requested by the workflow):

```bash
python tests/saleads_mi_negocio_full_test.py --cdp-url "http://127.0.0.1:9222"
```

You can also use:

```bash
export SALEADS_CDP_URL="http://127.0.0.1:9222"
python tests/saleads_mi_negocio_full_test.py
```

## Optional arguments

- `--google-email`: account to select if Google account chooser is shown  
  (default: `juanlucasbarbiergarzon@gmail.com`)
- `--headless`: run browser in headless mode (default is headed unless `SALEADS_HEADLESS=true`)

## Outputs

Each run creates:

- Screenshots in `qa/artifacts/saleads_mi_negocio_<timestamp>/`
- `report.json` containing step-by-step PASS/FAIL and legal final URLs
- Console summary with required report fields:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
