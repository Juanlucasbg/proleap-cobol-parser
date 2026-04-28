# SaleADS Mi Negocio full workflow automation

This repository primarily contains Java tests for the COBOL parser.  
To support the `saleads_mi_negocio_full_test` cron automation request, a standalone Playwright script was added under `automation/`.

## Script

- `automation/saleads_mi_negocio_full_test.mjs`

It validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal fields/buttons
4. Administrar Negocios sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones navigation + URL capture
9. Politica de Privacidad navigation + URL capture
10. Final PASS/FAIL report

The script is environment-agnostic and does not hardcode any SaleADS domain.

## Installation

```bash
npm install
npx playwright install chromium
```

## Execution options

### Option A: Provide runtime URL (recommended for unattended runs)

```bash
SALEADS_BASE_URL="https://<current-environment-login-url>" npm run saleads:mi-negocio
```

### Option B: Attach to already-open browser (manual/assisted runs)

If a browser is already open on the SaleADS login page, launch Chromium with remote debugging and pass websocket endpoint:

```bash
SALEADS_WS_ENDPOINT="ws://127.0.0.1:9222/devtools/browser/<id>" npm run saleads:mi-negocio
```

## Optional environment variables

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_OUTPUT_DIR` (default: `artifacts/saleads_mi_negocio_full_test/<timestamp>`)
- `HEADLESS=false` to run headed

## Evidence output

Each run saves:

- Checkpoint screenshots (`01_...png` through `06_...png`)
- `report.json` with per-validation PASS/FAIL plus final legal URLs

The script exits with non-zero status if any validation fails.
