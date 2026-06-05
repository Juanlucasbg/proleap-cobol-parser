# SaleADS Mi Negocio Full Workflow Test

This folder contains an end-to-end workflow script for validating the "Mi Negocio" module in any SaleADS environment.

## What this script validates

The flow covers:

1. Login with Google.
2. Opening "Mi Negocio" menu.
3. Validating "Agregar Negocio" modal.
4. Opening "Administrar Negocios".
5. Validating "Información General".
6. Validating "Detalles de la Cuenta".
7. Validating "Tus Negocios".
8. Validating "Términos y Condiciones" (including new tab handling).
9. Validating "Política de Privacidad" (including new tab handling).
10. Generating a final PASS/FAIL summary report.

The script prefers visible text-based selectors and waits for UI loading after each click.

## Precondition

The browser must already be open on the SaleADS login page and exposed through a Chromium CDP endpoint.

Default endpoint:

- `http://127.0.0.1:9222`

You can override it with:

- `CHROME_CDP_URL`
- `PLAYWRIGHT_CDP_URL`

## Install

```bash
cd /workspace/saleads-tests
npm install
```

## Run

```bash
cd /workspace/saleads-tests
npm run test:mi-negocio
```

## Outputs

- Screenshots: `saleads-tests/screenshots/run-<timestamp>/`
- JSON report: `saleads-tests/reports/mi-negocio-report-<timestamp>.json`

The JSON report includes per-step validation results and final PASS/FAIL status for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
