# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright script:

- `saleads_mi_negocio_full_test.py`

It validates the full flow:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Generate final PASS/FAIL report JSON

## Install

```bash
python3 -m pip install -r automation/requirements-saleads-e2e.txt
python3 -m playwright install chromium
```

## Run

### Option A: launch a fresh browser and navigate to current environment login URL

```bash
python3 automation/saleads_mi_negocio_full_test.py \
  --start-url "https://<current-saleads-environment>/login" \
  --headed
```

### Option B: attach to an existing browser session already on the login page

```bash
python3 automation/saleads_mi_negocio_full_test.py \
  --ws-endpoint "http://127.0.0.1:9222" \
  --headed
```

## Environment variables (optional)

- `SALEADS_START_URL`
- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADED` (`true`/`false`)
- `SALEADS_SLOW_MO_MS` (default: `150`)
- `SALEADS_OUTPUT_DIR` (default: `automation/artifacts`)
- `PW_WS_ENDPOINT`

## Evidence and report

Each run creates:

- Checkpoint screenshots in `automation/artifacts/saleads_mi_negocio_full_test_<timestamp>/`
- `final_report.json` with PASS/FAIL for all required fields plus captured legal URLs
