# SaleADS Mi Negocio full workflow test

This folder contains the Playwright test:

- `saleads_mi_negocio_full_test.spec.js`

## What it validates

The test automates the full requested flow:

1. Login with Google.
2. Open `Mi Negocio` menu and validate submenu.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Informacion General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Terminos y Condiciones`.
9. Validate `Politica de Privacidad`.
10. Generate final PASS/FAIL report JSON.

Screenshots are captured at key checkpoints in:

- `artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Final report is written to:

- `results/saleads_mi_negocio_full_test_report.json`

## Environment variables

To keep the test environment-agnostic, no domain is hardcoded.

Set one of:

- `SALEADS_LOGIN_URL`
- `SALEADS_URL`
- `BASE_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://your-saleads-environment/login" npm test
```

## Run

Install dependencies:

```bash
npm install
npx playwright install --with-deps chromium
```

Run headless:

```bash
npm test
```

Run headed:

```bash
npm run test:headed
```
