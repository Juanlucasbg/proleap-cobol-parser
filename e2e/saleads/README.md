# SaleADS E2E tests

This folder contains end-to-end tests for SaleADS.ai.

## Test included

- `saleads_mi_negocio_full_test`
  - File: `tests/saleads-mi-negocio-full.spec.js`
  - Scope:
    - Google login
    - Mi Negocio menu validation
    - Agregar Negocio modal validation
    - Administrar Negocios sections validation
    - Informacion General, Detalles de la Cuenta, and Tus Negocios validation
    - Terminos y Condiciones and Politica de Privacidad validation
    - Screenshots and final PASS/FAIL report JSON

## Run

From this folder:

```bash
npm install
npx playwright install chromium
npm run test:mi-negocio
```

## Environment

The test is environment-agnostic. Set a login URL from any SaleADS environment:

```bash
SALEADS_URL="https://<current-env>/login" npm run test:mi-negocio
```

If `SALEADS_URL` is not set, the test assumes the browser is already on a SaleADS login page and will fail if it starts from `about:blank`.

## Evidence output

Artifacts are generated in:

- `test-results/saleads_mi_negocio_full_test/`

Including screenshots and:

- `saleads_mi_negocio_full_test_report.json`
