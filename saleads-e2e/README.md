# SaleADS E2E - Mi Negocio Workflow

This folder contains the automated Playwright test:

- `tests/saleads_mi_negocio_full_test.spec.js`

## What it validates

The test covers the full workflow requested:

1. Login with Google
2. Open **Mi Negocio** menu
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Informacion General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Terminos y Condiciones**
9. Validate **Politica de Privacidad**
10. Produce PASS/FAIL report fields for each validation

It captures screenshots at important checkpoints and stores a structured
`final-report.json` in Playwright output artifacts.

## Environment agnostic behavior

- The test does **not** hardcode a domain.
- If browser already starts on the SaleADS login page, it continues from there.
- If it starts on `about:blank`, set one of:
  - `SALEADS_BASE_URL`
  - `BASE_URL`

## Run locally

```bash
cd saleads-e2e
npm install
npx playwright install --with-deps chromium
npm run test:mi-negocio
```

Headed run:

```bash
npm run test:mi-negocio:headed
```
