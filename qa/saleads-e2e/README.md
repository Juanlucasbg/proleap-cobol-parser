# SaleADS Mi Negocio Full Workflow Test

This folder contains a Playwright E2E test for the workflow named:

- `saleads_mi_negocio_full_test`

The test is designed to avoid hardcoded domains. Provide the environment login URL at runtime.

## What it validates

1. Login with Google (including optional account pick for `juanlucasbarbiergarzon@gmail.com`)
2. Expand `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (same tab or new tab)
9. Validate `Política de Privacidad` (same tab or new tab)
10. Emit final PASS/FAIL report grouped by required fields

The test captures screenshots at required checkpoints and stores legal final URLs.

## Install

```bash
cd qa/saleads-e2e
npm install
npx playwright install chromium
```

## Run

```bash
cd qa/saleads-e2e
SALEADS_URL="https://<current-env-login-page>" npm run test:saleads
```

Run headed mode:

```bash
cd qa/saleads-e2e
SALEADS_URL="https://<current-env-login-page>" npm run test:saleads:headed
```

## Artifacts

- Screenshots: `qa/saleads-e2e/artifacts/screenshots/<timestamp>/`
- Final JSON report: `qa/saleads-e2e/artifacts/reports/saleads_mi_negocio_full_test-<timestamp>.json`

## Notes

- No specific domain is baked into the test.
- Selectors are primarily visible-text based.
- Every click includes a UI-load wait helper.
