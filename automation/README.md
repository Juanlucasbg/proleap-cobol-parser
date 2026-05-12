# SaleADS Mi Negocio full workflow test

This directory contains an environment-agnostic Playwright automation for the workflow:

1. Login with Google
2. Open `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open and validate `Administrar Negocios` sections
5. Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
6. Produce a final PASS/FAIL report per requested validation block

## Script

- `saleads_mi_negocio_full_test.py`

## Configuration

Use environment variables instead of hardcoded URLs/domains:

- `SALEADS_LOGIN_URL` (required unless the script is adapted to reuse an already-open session)
- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_TIMEOUT_MS` (default: `30000`)
- `SALEADS_HEADLESS` (default: `true`)
- `SALEADS_OUTPUT_DIR` (default: `artifacts/saleads_mi_negocio_full_test`)

## Run

```bash
python3 -m pip install -r automation/requirements.txt
python3 -m playwright install chromium
SALEADS_LOGIN_URL="https://<current-env-login-page>" \
python3 automation/saleads_mi_negocio_full_test.py
```

## Output

- JSON report: `artifacts/saleads_mi_negocio_full_test/report.json`
- Screenshots: `artifacts/saleads_mi_negocio_full_test/screenshots/*.png`

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
