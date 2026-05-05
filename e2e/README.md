# SaleADS Mi Negocio full workflow test

This directory contains an environment-agnostic Playwright test script for:

- Login with Google
- Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Información General / Detalles de la Cuenta / Tus Negocios validation
- Términos y Condiciones and Política de Privacidad validation (including new-tab handling)
- Screenshot capture at key checkpoints
- PASS/FAIL final report output

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m playwright install chromium
```

## Run

```bash
SALEADS_START_URL="https://<your-saleads-environment>/login" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
python tests/saleads_mi_negocio_full_test.py
```

### Optional environment variables

- `HEADLESS` (default: `true`)
- `PW_TIMEOUT_MS` (default: `30000`)
- `SALEADS_E2E_OUTPUT_DIR` (default: `e2e/artifacts`)
- `SALEADS_EXPECTED_USER_NAME` (optional stricter check)

## Output

- Screenshots: `e2e/artifacts/screenshots/`
- JSON report: `e2e/artifacts/saleads_mi_negocio_full_test_report.json`
