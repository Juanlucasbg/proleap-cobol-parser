# SaleADS Mi Negocio Full Test

This folder contains the automated workflow test:

- `saleads_mi_negocio_full_test.js`

## What it validates

The script executes the full requested flow:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones`
9. Validate `Política de Privacidad`
10. Generate final PASS/FAIL report

It captures screenshots at key checkpoints and writes a JSON report with one result per requested field.

## Runtime configuration

The test is URL-agnostic and does not hardcode any SaleADS domain.

Set either:

- `SALEADS_LOGIN_URL` (or `BASE_URL`) to point to the current environment login page, or
- `PLAYWRIGHT_WS_ENDPOINT` to attach to an existing browser session already on the login page.

Optional:

- `HEADLESS=false` to run in headed mode.

## Install browsers (first run)

```bash
npx playwright install --with-deps chromium
```

## Run

```bash
npm run test:mi-negocio
```

## Artifacts

Output is written to:

- `artifacts/<timestamp>/final-report.json`
- `artifacts/<timestamp>/*.png`
