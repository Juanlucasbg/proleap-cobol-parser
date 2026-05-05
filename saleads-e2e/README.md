# SaleADS Mi Negocio E2E

Domain-agnostic Playwright test (Python) for validating the full **Mi Negocio** workflow in any SaleADS environment.

## What this test covers

- Login with Google (including optional account selection).
- Sidebar and "Mi Negocio" menu expansion.
- "Agregar Negocio" modal validation.
- "Administrar Negocios" account page sections.
- Validations for:
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
- New-tab handling for legal links and return to app.
- Evidence capture via screenshots and JSON final report.

## Requirements

- Python 3.10+
- Browser binaries installed for Playwright

## Install

```bash
cd saleads-e2e
python3 -m pip install -r requirements.txt
python3 -m playwright install chromium
```

## Run

```bash
cd saleads-e2e
SALEADS_LOGIN_URL="https://<current-environment-login-page>" \
SALEADS_HEADLESS=false \
SALEADS_SLOWMO_MS=250 \
python3 -m pytest -s test_saleads_mi_negocio_workflow.py
```

## Environment variables

- `SALEADS_LOGIN_URL` (required): login page URL for whichever environment is under test.
- `SALEADS_HEADLESS` (optional): `true`/`false`, default `false`.
- `SALEADS_SLOWMO_MS` (optional): click/type delay in ms, default `250`.

## Output artifacts

- `artifacts/screenshots/*.png`
- `artifacts/final_report.json`
