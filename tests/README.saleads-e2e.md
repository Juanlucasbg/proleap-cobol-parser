# SaleADS - Mi Negocio E2E workflow

This repository includes a Playwright E2E test that validates the complete `Mi Negocio` flow required by automation `saleads_mi_negocio_full_test`.

## Test file

- `tests/saleads-mi-negocio-full.spec.js`

## Environment variables

- `SALEADS_LOGIN_URL` (required): login page URL for the current environment (dev/staging/prod).
- `GOOGLE_ACCOUNT_EMAIL` (optional): account to select in Google chooser. Default:
  `juanlucasbarbiergarzon@gmail.com`.

## Run locally

```bash
npm install
npx playwright install --with-deps chromium
npm run test:e2e -- tests/saleads-mi-negocio-full.spec.js
```

## Evidence generated

The test captures screenshots at key checkpoints and emits a final report:

- Screenshots: `test-results/.../*.png`
- Final report JSON: `test-results/.../final-report.json`

The report contains PASS/FAIL status for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
