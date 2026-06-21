# SaleADS Mi Negocio workflow automation

This repository now includes a standalone Playwright workflow script for validating the SaleADS.ai "Mi Negocio" module end-to-end.

## What it validates

The script executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It also captures screenshots at important checkpoints and stores final legal-page URLs in the report output.

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- You must provide the current environment login page dynamically through an environment variable.

## Run

Install dependencies:

```bash
npm install
npx playwright install chromium
```

Execute:

```bash
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm run saleads:mi-negocio
```

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS` (`false` to run headed; default is headless)
- `SALEADS_ARTIFACTS_DIR` (default: `artifacts/saleads-mi-negocio`)
- `SALEADS_WAIT_AFTER_CLICK_MS` (default: `1200`)

## Outputs

- Screenshots and JSON report files are written to `artifacts/saleads-mi-negocio/` by default.
- The report includes per-step PASS/FAIL and legal URLs.
