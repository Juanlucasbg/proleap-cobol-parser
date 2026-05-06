# SaleADS Mi Negocio E2E automation

This folder contains the Playwright end-to-end test for the automation scenario:

- `saleads_mi_negocio_full_test`

## What it validates

The flow validates:

1. Login with Google.
2. Opening **Mi Negocio** menu and checking submenu options.
3. **Agregar Negocio** modal content.
4. **Administrar Negocios** page sections.
5. **Informacion General** details.
6. **Detalles de la Cuenta** details.
7. **Tus Negocios** details.
8. **Terminos y Condiciones** navigation/content and final URL capture.
9. **Politica de Privacidad** navigation/content and final URL capture.
10. PASS/FAIL summary output for each validation block.

The test stores screenshots at key checkpoints and writes:

- `e2e-artifacts/saleads_mi_negocio_full_test/final-report.json`

## Environment-agnostic usage

No specific SaleADS domain is hardcoded.

- If browser is already on the login page, test starts from there.
- If browser starts at `about:blank`, provide one of:
  - `SALEADS_BASE_URL`
  - `BASE_URL`

Example:

```bash
SALEADS_BASE_URL="https://<your-saleads-environment>/login" npm run test:saleads-mi-negocio
```

## Install and run

```bash
npm install
npm run pw:install
npm run test:saleads-mi-negocio
```
