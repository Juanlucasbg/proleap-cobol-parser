# SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end test that automates the full
"Mi Negocio" workflow requested in the `saleads_mi_negocio_full_test` prompt.

## What this test validates

The script runs these checkpoints and returns PASS/FAIL for each:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

The final report is printed to stdout as JSON under the marker:

`SALEADS_MI_NEGOCIO_FINAL_REPORT`

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- Set `SALEADS_START_URL` to the login page URL of the environment you want to test.
- If a Google account chooser appears, the test selects:
  `juanlucasbarbiergarzon@gmail.com`.

## Run locally

From this folder:

```bash
npm install
npx playwright install --with-deps
SALEADS_START_URL="https://<your-env-login-url>" npm test
```

For headed mode:

```bash
SALEADS_START_URL="https://<your-env-login-url>" npm run test:headed
```

## Evidence artifacts

Important checkpoint screenshots are stored in:

- `test-results/checkpoints/*.png`

Playwright HTML report is generated in:

- `playwright-report/`
