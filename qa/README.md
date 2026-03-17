# SaleADS Mi Negocio full workflow test

This folder contains an end-to-end Playwright test:

- `saleads_mi_negocio_full_test.spec.js`

## What it validates

The test covers:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios account view
5. Informacion General section
6. Detalles de la Cuenta section
7. Tus Negocios section
8. Terminos y Condiciones legal page
9. Politica de Privacidad legal page
10. Final PASS/FAIL report in `final-report.json` attachment

It also captures screenshots at required checkpoints and handles legal links opening in either the same tab or a new tab.

## Environment-agnostic behavior

No specific SaleADS domain is hardcoded.

- If `SALEADS_START_URL` (or `SALEADS_LOGIN_URL`/`BASE_URL`) is provided, the test navigates there.
- If no URL variable is provided, the test assumes the runner pre-opened the login page.

## Run example

```bash
cd qa
npm install
npm run test
```

Optional:

```bash
cd qa
SALEADS_START_URL="https://<your-saleads-env>/login" npm run test
```
