# SaleADS E2E Tests

This folder contains browser-based end-to-end tests for SaleADS workflows.

## Implemented workflow

- `saleads_mi_negocio_full_test`  
  File: `tests/saleads-mi-negocio-workflow.spec.js`

## Run

Install dependencies:

```bash
npm install
npx playwright install chromium
```

Run the Mi Negocio workflow:

```bash
npm run test:saleads-mi-negocio
```

Run headed (debug):

```bash
npm run test:saleads-mi-negocio:headed
```

## Environment handling

The test does **not** hardcode a SaleADS domain.

- If the browser is already on the login page, it continues from there.
- In CI or local headless runs, provide a login URL via:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:saleads-mi-negocio
```

## Evidence and report

- Screenshots are captured at key checkpoints (dashboard, menu, modal, account, legal pages).
- Final PASS/FAIL matrix is printed in logs for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
