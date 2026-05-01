# SaleADS Mi Negocio full workflow test

This directory contains the `saleads_mi_negocio_full_test` Playwright automation.

## What this test validates

The suite covers the complete workflow requested for the Mi Negocio module:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Emit final PASS/FAIL report

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- If `SALEADS_BASE_URL` (or `BASE_URL`) is provided, the test navigates there.
- If no URL is provided, the test assumes the browser already starts on the SaleADS login page.

## Evidence generated

- Checkpoint screenshots are attached to the Playwright report:
  - Dashboard after login
  - Expanded Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios account page (full page)
  - Terminos y Condiciones page
  - Politica de Privacidad page
- A final JSON report is attached with PASS/FAIL per required field.
- Final legal URLs are captured in the final report details.

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps
SALEADS_BASE_URL="https://current-saleads-environment" npm run test:mi-negocio
```
