# SaleADS Mi Negocio E2E Workflow

This directory contains a Playwright end-to-end workflow test for:

- Google login into SaleADS.ai
- Mi Negocio sidebar expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Informacion General, Detalles de la Cuenta, and Tus Negocios checks
- Terminos y Condiciones and Politica de Privacidad legal page checks

## Environment-agnostic behavior

The test does **not** hardcode a SaleADS domain.

- If `SALEADS_LOGIN_URL` is provided, the test starts from that URL.
- If `SALEADS_LOGIN_URL` is not provided, the test expects the browser to already be on a SaleADS login page.

## Run

```bash
cd e2e
npm run install:browsers
SALEADS_LOGIN_URL="https://<current-environment>/login" npm test
```

If the runner already starts the browser on the login page, you can run without `SALEADS_LOGIN_URL`.

## Artifacts

Generated files:

- `artifacts/screenshots/*.png` checkpoint screenshots
- `artifacts/saleads_mi_negocio_report.json` final PASS/FAIL report by validation field
