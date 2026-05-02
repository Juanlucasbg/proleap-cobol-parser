# SaleADS Mi Negocio E2E Workflow Test

This folder contains an environment-agnostic Playwright test implemented in Python:

- `saleads_mi_negocio_full_test.py`

The script validates the complete workflow requested in `saleads_mi_negocio_full_test`, including:

- Google login entry step (and optional account chooser selection)
- Sidebar -> `Negocio` -> `Mi Negocio` navigation
- `Agregar Negocio` modal checks
- `Administrar Negocios` view checks
- Validation of:
  - `Información General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Screenshot evidence at key checkpoints
- Final PASS/FAIL report by requested report fields

## Requirements

- Python 3.10+
- Network access to the target SaleADS environment

Install dependencies:

```bash
pip3 install -r e2e/requirements.txt
python3 -m playwright install chromium
```

## Run

```bash
SALEADS_START_URL="https://<current-environment-login-url>" \
python3 e2e/saleads_mi_negocio_full_test.py
```

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS` (`true` by default; set `false` for headed mode)
- `SALEADS_TIMEOUT_MS` (default `20000`)
- `SALEADS_SLOW_MO_MS` (default `0`)

## Output artifacts

Each run generates:

- `e2e/artifacts/run-<timestamp>/report.json`
- `e2e/artifacts/run-<timestamp>/screenshots/*.png`

The JSON report includes:

- Per-field PASS/FAIL summary for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- Captured screenshot paths
- Final URLs for legal pages
