# SaleADS - Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright test for the workflow:

1. Login with Google.
2. Open `Mi Negocio` from the left sidebar.
3. Validate the `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Generate PASS/FAIL report and evidence screenshots.

## Requirements

- Node.js 18+
- Chromium browser for Playwright

## Install

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

`SALEADS_LOGIN_URL` is required so the same script can run against any environment (dev, staging, production) without hardcoded domains.

```bash
cd /workspace/e2e/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:saleads
```

Optional variables:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` (to watch the flow in headed mode)

## Evidence and report output

- Screenshots: `artifacts/screenshots/`
- Playwright HTML report: `artifacts/playwright-report/`
- Final JSON report:
  - `artifacts/saleads_mi_negocio_report.json`

The JSON report includes PASS/FAIL per requested validation section and the final URLs used for legal pages.
