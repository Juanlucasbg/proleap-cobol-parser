# SaleADS - Mi Negocio E2E

This folder contains a Playwright end-to-end workflow test for SaleADS:

- Login with Google
- Open `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate:
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
- Emit final PASS/FAIL report per section

## Environment variables

The test is environment-agnostic and does not hardcode a domain.

Set one of:

- `SALEADS_URL`
- `SALEADS_LOGIN_URL`
- `BASE_URL`

Value must be the login page for the current SaleADS environment (dev/staging/prod).

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
SALEADS_URL="https://your-environment-login-page" npm test
```

## Artifacts

- Checkpoint screenshots are saved under:
  - `e2e/artifacts/saleads-mi-negocio/`
- Playwright report:
  - `e2e/playwright-report/`
