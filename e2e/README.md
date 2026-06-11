# SaleADS - Mi Negocio full workflow (Playwright)

This folder contains an end-to-end test that validates the complete **Mi Negocio** workflow described by automation `saleads_mi_negocio_full_test`.

## Test file

- `saleads-mi-negocio-full.spec.js`

## Key behavior covered

- Login with Google (including optional account selector click for `juanlucasbarbiergarzon@gmail.com`).
- Sidebar validation and navigation to **Negocio > Mi Negocio**.
- Validation of **Agregar Negocio** modal fields and controls.
- Validation of **Administrar Negocios** sections.
- Validation of:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- New tab handling for legal links, then return to the app tab.
- Checkpoint screenshots and final PASS/FAIL JSON report.

## Environment handling

No domain is hardcoded.

- If `SALEADS_LOGIN_URL` is provided, the test navigates to that login page.
- If `SALEADS_LOGIN_URL` is not provided, the test uses the current page and proceeds with login actions.

## Run

1. Install browsers:
   - `npm run e2e:install-browsers`
2. Run test:
   - `SALEADS_LOGIN_URL="https://<your-env>/login" npm run e2e:test`

## Artifacts

- JSON report: `e2e/artifacts/mi-negocio-final-report.json`
- Screenshots: `e2e/artifacts/screenshots/*.png`
