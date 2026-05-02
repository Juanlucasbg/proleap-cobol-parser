# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic browser automation script for:

- Login with Google
- Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Informacion General validation
- Detalles de la Cuenta validation
- Tus Negocios validation
- Terminos y Condiciones validation (including popup/new-tab handling)
- Politica de Privacidad validation (including popup/new-tab handling)
- Final PASS/FAIL report generation per required field

## Files

- `saleads_mi_negocio_full_test.py`: Main test script.
- `requirements.txt`: Python dependencies.
- `artifacts/<timestamp>/`: Runtime screenshots and `final_report.json`.

## Prerequisites

Python 3.10+ and Playwright browser binaries.

Install dependencies:

```bash
pip3 install -r automation/saleads/requirements.txt
python3 -m playwright install chromium
```

## Run

Use a URL from any SaleADS environment (dev/staging/prod), without hardcoding domain in the script.

Option 1 (env var):

```bash
export SALEADS_BASE_URL="https://<your-current-saleads-login-url>"
python3 automation/saleads/saleads_mi_negocio_full_test.py
```

Option 2 (CLI arg):

```bash
python3 automation/saleads/saleads_mi_negocio_full_test.py --url "https://<your-current-saleads-login-url>"
```

Optional headed mode:

```bash
python3 automation/saleads/saleads_mi_negocio_full_test.py --url "https://<your-current-saleads-login-url>" --headed
```

## Output

The script prints JSON to stdout and writes:

- `automation/saleads/artifacts/<timestamp>/final_report.json`
- Checkpoint screenshots requested by the workflow

The final report contains PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
