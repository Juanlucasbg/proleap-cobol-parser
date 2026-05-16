# SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end test that validates the full SaleADS.ai "Mi Negocio" workflow, including:

- Login with Google
- Mi Negocio menu expansion
- Agregar Negocio modal validations
- Administrar Negocios validations
- Informacion General / Detalles de la Cuenta / Tus Negocios checks
- Legal links (Terminos y Condiciones + Politica de Privacidad), including support for same-tab or new-tab navigation
- Checkpoint screenshots and a final PASS/FAIL JSON report

## Test file

- `tests/saleads_mi_negocio_full_test.spec.js`

## Environment variables

- `SALEADS_LOGIN_URL` (or `BASE_URL`): Login URL for the current SaleADS environment.
  - The test never hardcodes a domain.
- `SALEADS_GOOGLE_ACCOUNT` (optional): Google account to select in the account picker.
  - Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_EXPECTED_EMAIL` (optional): Expected user email in Informacion General.
  - Default: `SALEADS_GOOGLE_ACCOUNT`
- `SALEADS_EXPECTED_USER_NAME` (optional): Expected user name in Informacion General.
- `HEADLESS` (optional): set to `false` for headed mode.

## Run locally

```bash
cd e2e
npm install
npx playwright install
SALEADS_LOGIN_URL="https://<your-current-env>/login" npm test
```

## Artifacts

- Playwright HTML report: `e2e/playwright-report/`
- Final JSON report:
  - Playwright output attachment
  - Workspace copy at `e2e/artifacts/saleads-mi-negocio-final-report.json`
