# SaleADS Mi Negocio E2E Test

This folder contains the `saleads_mi_negocio_full_test` workflow test implemented with Playwright.

## What it validates

The test automates and validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios page
5. Informacion General section
6. Detalles de la Cuenta section
7. Tus Negocios section
8. Terminos y Condiciones legal page
9. Politica de Privacidad legal page
10. Final PASS/FAIL report output

It also captures screenshots at key checkpoints and supports both same-tab and new-tab legal link behavior.

## Environment agnostic setup

No domain is hardcoded. Provide the current environment login URL at runtime:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

If your executor already opens the login page before starting the test, the variable can be omitted.

## Install and run

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

## Evidence artifacts

- Screenshots are attached to Playwright test results.
- HTML report is generated in `playwright-report/`.
- Final workflow report JSON is attached as `saleads_mi_negocio_workflow_report.json`.
