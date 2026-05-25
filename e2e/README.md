# SaleADS Mi Negocio E2E

This repository now includes a Playwright workflow test:

- `e2e/tests/saleads-mi-negocio-full.spec.js`

## What it validates

The test automates the complete `saleads_mi_negocio_full_test` flow:

1. Login with Google.
2. Expand `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (including new-tab behavior).
9. Validate `Política de Privacidad` (including new-tab behavior).
10. Generate final PASS/FAIL report.

It captures screenshots at important checkpoints and writes a final report JSON under:

- `e2e-artifacts/saleads-mi-negocio-full-test/<run-id>/final-report.json`

## Environment-agnostic setup

No SaleADS domain is hardcoded.

Before running, set one of these environment variables to your current environment login page:

- `SALEADS_LOGIN_URL` (recommended)
- `SALEADS_BASE_URL`
- `BASE_URL`

Example:

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
```

## Run

```bash
npx playwright install --with-deps chromium
npm run test:e2e
```

For headed mode:

```bash
npm run test:e2e:headed
```
