# SaleADS E2E Tests

This folder contains a Playwright-based end-to-end test suite for SaleADS workflows.

## Included workflow

- `tests/saleads_mi_negocio_full_test.spec.js`
  - Login with Google
  - Mi Negocio menu validation
  - Agregar Negocio modal validation
  - Administrar Negocios view validation
  - Información General / Detalles de la Cuenta / Tus Negocios checks
  - Términos y Condiciones / Política de Privacidad validation (same tab or new tab)
  - Screenshot evidence and final JSON PASS/FAIL report

## Environment-agnostic setup

The suite does **not** hardcode any SaleADS domain.

Provide one of:

- `SALEADS_LOGIN_URL` (preferred), or
- `APP_URL`

If neither variable is set, the test expects to start from a pre-opened login page.

## Commands

```bash
npm install
npm run install:browsers
npm run test:mi-negocio
```

For headed mode:

```bash
npm run test:headed -- tests/saleads_mi_negocio_full_test.spec.js
```

## Optional environment variables

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS` (`false` to run headed)

## Evidence output

Artifacts are generated in:

- `test-results/saleads_mi_negocio_full_test/*.png`
- `test-results/saleads_mi_negocio_full_test/final_report.json`
