# SaleADS Mi Negocio workflow test

This folder contains the Playwright E2E test for:

- `saleads_mi_negocio_full_test`

## What it validates

The test automates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal validation
4. Administrar Negocios page sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones (same tab or new tab)
9. Politica de Privacidad (same tab or new tab)

It captures screenshots at key checkpoints and writes a JSON final report with PASS/FAIL per required field.

## Run

Install Playwright browsers (first run only):

```bash
npx playwright install
```

Execute test:

```bash
SALEADS_START_URL="https://<your-saleads-login-page>" npm run test:e2e -- e2e/saleads-mi-negocio-full.spec.js
```

If `SALEADS_START_URL` is not provided, `BASE_URL` is also supported.

## Artifacts

Generated outputs are written under:

- `e2e-artifacts/saleads_mi_negocio_full_test/<timestamp>/`
  - checkpoint screenshots
  - `final-report.json`
