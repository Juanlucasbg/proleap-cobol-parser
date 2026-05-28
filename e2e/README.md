# SaleADS E2E workflow: Mi Negocio

This repository now includes a Playwright test that validates the complete **Mi Negocio** workflow after Google login.

## Run

1. Install dependencies:

```bash
npm install
npm run playwright:install
```

2. Run the workflow test against any SaleADS environment by providing the login URL through env vars (no hardcoded domain):

```bash
SALEADS_LOGIN_URL="https://your-saleads-environment/login" npm run test:e2e:saleads
```

Alternative supported vars: `SALEADS_URL` or `BASE_URL`.

## Test output

- Screenshots for key checkpoints are attached to the Playwright results.
- `final-report.json` is attached with PASS/FAIL per requested validation:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
