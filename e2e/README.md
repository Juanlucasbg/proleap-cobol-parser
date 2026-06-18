# SaleADS E2E - Mi Negocio

This folder contains an end-to-end Playwright test for the `saleads_mi_negocio_full_test` workflow.

## What it validates

The test automates and validates:

1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal.
4. Administrar Negocios view.
5. Informacion General block.
6. Detalles de la Cuenta block.
7. Tus Negocios block.
8. Terminos y Condiciones page.
9. Politica de Privacidad page.

It also captures screenshots at required checkpoints and writes a final PASS/FAIL report to:

- `test-results/**/saleads-mi-negocio-report.json`

## Environment compatibility

No domain is hardcoded.

Provide the target login page URL using one of these environment variables:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_URL`
- `BASE_URL`

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm run test:saleads-mi-negocio
```

Optional:

- `HEADLESS=false` to run with browser UI.

## Notes

- The script attempts to click the Google account `juanlucasbarbiergarzon@gmail.com` when the account chooser is shown.
- If legal links open in a new tab, it validates that tab and returns to the application page.
