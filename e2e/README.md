# SaleADS Mi Negocio E2E

This folder contains the automated test `saleads_mi_negocio_full_test` for validating
the full Mi Negocio workflow across any SaleADS.ai environment.

## What this test covers

- Login with Google (including optional account selection)
- Sidebar and Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios sections validation
- Información General, Detalles de la Cuenta and Tus Negocios checks
- Legal links validation:
  - Términos y Condiciones
  - Política de Privacidad
- Screenshot evidence and final PASS/FAIL report

## Requirements

- Node.js 18+
- Playwright Chromium browser:

```bash
npx playwright install chromium
```

## Run

The test works in any environment by setting an environment URL at runtime.

```bash
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:e2e
```

If the browser is already on the login page in your execution platform, the test can
also run without `SALEADS_BASE_URL`; it will continue from the current page.

Headed mode:

```bash
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:e2e:headed
```

## Artifacts

- Screenshots are saved under `e2e/artifacts/`
- Playwright report is generated under `e2e/playwright-report/`
- Final status report is written to `e2e/artifacts/final-report.json`
