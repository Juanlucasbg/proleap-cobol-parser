# SaleADS Mi Negocio E2E

This folder contains `saleads_mi_negocio_full_test.spec.ts`, a Playwright test that validates the complete **Mi Negocio** workflow:

- Google login
- Sidebar + Mi Negocio menu expansion
- Agregar Negocio modal checks
- Administrar Negocios page checks
- Información General / Detalles de la Cuenta / Tus Negocios validations
- Términos y Condiciones + Política de Privacidad (same tab or popup tab)
- Checkpoint screenshots + final PASS/FAIL report JSON

## Environment-agnostic behavior

- No hardcoded SaleADS domain is used.
- If the browser is already on the SaleADS login page, the test runs directly.
- If Playwright starts from `about:blank`, provide `SALEADS_URL` at runtime.

## Run example

```bash
npm init -y
npm install -D @playwright/test
npx playwright install
SALEADS_URL="https://<your-environment-host>" npx playwright test e2e/saleads_mi_negocio_full_test.spec.ts
```

## Outputs

Playwright test output includes:

- Checkpoint screenshots for major steps
- `saleads-mi-negocio-report.json` with PASS/FAIL per required field and legal URLs
