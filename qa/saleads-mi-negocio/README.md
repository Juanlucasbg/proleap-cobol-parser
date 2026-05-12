# SaleADS Mi Negocio Full Workflow Test

This Playwright suite implements the `saleads_mi_negocio_full_test` flow end-to-end and is designed to run against any SaleADS.ai environment.

## What it validates

1. Login with Google and dashboard/sidebar visibility.
2. `Negocio > Mi Negocio` expansion (`Agregar Negocio`, `Administrar Negocios`).
3. `Agregar Negocio` modal fields and buttons.
4. `Administrar Negocios` page sections.
5. `Información General` content.
6. `Detalles de la Cuenta` content.
7. `Tus Negocios` content.
8. `Términos y Condiciones` link behavior and content.
9. `Política de Privacidad` link behavior and content.
10. Final PASS/FAIL report attached as `final-report.json`.

## Environment-agnostic behavior

- No domain is hardcoded.
- If `SALEADS_START_URL` is provided, the test navigates there.
- If `SALEADS_START_URL` is not provided, the test assumes the current page is already at the SaleADS login screen.

## Run

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps
SALEADS_START_URL="https://<your-saleads-env>/login" npm test
```

Optional headed execution:

```bash
PW_HEADLESS=false SALEADS_START_URL="https://<your-saleads-env>/login" npm run test:headed
```

## Artifacts

- Screenshots are attached at key checkpoints during execution.
- HTML Playwright report is generated at `artifacts/html-report`.
