# SaleADS Mi Negocio Full Workflow E2E

This directory contains an environment-agnostic Playwright test for the complete
Mi Negocio workflow described in `saleads_mi_negocio_full_test`.

## What this test validates

- Login with Google flow entry and post-login app shell visibility.
- Sidebar navigation for `Negocio` -> `Mi Negocio`.
- `Agregar Negocio` modal content and controls.
- `Administrar Negocios` page sections:
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Seccion Legal
- Legal links:
  - Terminos y Condiciones
  - Politica de Privacidad
- New-tab handling, URL capture, checkpoint screenshots, and final pass/fail report.

## Environment requirements

Set these environment variables before running:

- `SALEADS_BASE_URL` (required): current SaleADS login page URL for the target environment.
- `SALEADS_GOOGLE_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_GOOGLE_PASSWORD` (optional): only used if Google prompts for password.

## Run locally

```bash
cd e2e/saleads
npm install
npx playwright install --with-deps
SALEADS_BASE_URL="https://<current-env-login-url>" npm test
```

## Output

- Screenshots: `e2e/saleads/test-results/screenshots/`
- Final summary report: `e2e/saleads/test-results/mi-negocio-final-report.json`

The JSON report returns `PASS`/`FAIL` for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
