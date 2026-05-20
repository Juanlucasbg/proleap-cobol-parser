# SaleADS Mi Negocio E2E

This repository now includes a Playwright end-to-end test named:

- `saleads_mi_negocio_full_test`

## What it validates

The script automates and validates:

1. Login with Google.
2. Sidebar navigation visibility.
3. `Negocio` -> `Mi Negocio` menu expansion.
4. `Agregar Negocio` modal fields and controls.
5. `Administrar Negocios` account page sections.
6. `Información General`, `Detalles de la Cuenta`, and `Tus Negocios` blocks.
7. `Términos y Condiciones` legal document.
8. `Política de Privacidad` legal document.

It captures screenshots at key checkpoints and stores a final PASS/FAIL report as an attachment (`final-report.json`) in Playwright output.

## Environment-agnostic configuration

No domain is hardcoded.

Provide one of these variables when running in a fresh Playwright context:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`

If not provided, the test expects the browser to already be on the SaleADS login page before step 1.

## Install browsers

```bash
npm run playwright:install
```

## Run

```bash
npm run test:saleads:mi-negocio
```

For headed mode:

```bash
npm run test:saleads:mi-negocio:headed
```
