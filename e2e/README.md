# SaleADS Mi Negocio E2E Test

This folder contains an end-to-end Playwright test for the workflow:

1. Login with Google.
2. Navigate to **Negocio -> Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate account sections and legal links.
6. Produce a final PASS/FAIL report per required checkpoint.

## Why it is environment-agnostic

- No SaleADS domain is hardcoded.
- The login URL is provided at runtime with `SALEADS_LOGIN_URL`.
- Selectors prioritize visible text and semantic roles.

## Prerequisites

```bash
cd /workspace/e2e
npm install
npm run install:browsers
```

## Run

```bash
cd /workspace/e2e
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_EMAIL` (default: same as `SALEADS_GOOGLE_ACCOUNT`)
- `SALEADS_EXPECTED_NAME` (optional, if you want strict name validation)
- `HEADLESS=false` (to run with browser UI)

## Evidence and report output

- Screenshots: `e2e/artifacts/screenshots/`
- Final JSON report: `e2e/artifacts/saleads-mi-negocio-report.json`

The JSON report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
