# SaleADS Mi Negocio Full Workflow Test

This folder contains the automated workflow runner for:

- Login with Google
- Mi Negocio menu checks
- Agregar Negocio modal checks
- Administrar Negocios sections checks
- Legal links validation (including new tab handling)
- Final PASS/FAIL report with evidence

## Prerequisites

- Python 3.10+
- Chromium dependencies available on the host

## Install

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-environment>/login" \
SALEADS_GOOGLE_EMAIL="juanlucasbarbiergarzon@gmail.com" \
python3 automation/saleads_mi_negocio_full_test.py
```

Or connect to an already-open Chromium session (browser already on login page):

```bash
SALEADS_CDP_URL="http://127.0.0.1:9222" \
SALEADS_GOOGLE_EMAIL="juanlucasbarbiergarzon@gmail.com" \
python3 automation/saleads_mi_negocio_full_test.py
```

## Environment variables

- `SALEADS_LOGIN_URL` (required): login page URL for the current environment.
- `SALEADS_CDP_URL` (optional): connect to an existing Chromium session over CDP.
- `SALEADS_GOOGLE_EMAIL` (optional): Google account to select.  
  Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_HEADLESS` (optional): `true`/`false`, default `false`.
- `SALEADS_TIMEOUT_MS` (optional): default `20000`.
- `SALEADS_ARTIFACTS_DIR` (optional): output base directory, default `artifacts`.

You must provide at least one of:

- `SALEADS_LOGIN_URL`
- `SALEADS_CDP_URL`

## Output

Each run writes to:

- `artifacts/saleads_mi_negocio_<timestamp>/screenshots/*.png`
- `artifacts/saleads_mi_negocio_<timestamp>/final_report.json`

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
