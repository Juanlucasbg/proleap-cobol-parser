# SaleADS E2E Workflows

This folder contains Playwright tests for SaleADS UI workflows.

## Implemented scenario

- `saleads_mi_negocio_full_test`
  - Google login flow
  - Mi Negocio sidebar expansion
  - Agregar Negocio modal validation
  - Administrar Negocios view validation
  - Información General / Detalles de la Cuenta / Tus Negocios checks
  - Términos y Condiciones and Política de Privacidad validation
  - Screenshots at important checkpoints
  - Final PASS/FAIL report JSON

## Environment-agnostic behavior

- The test does **not** hardcode a SaleADS domain.
- If the test session already starts on the login page, it uses the current page.
- If the page is blank, set `SALEADS_URL` to the current environment login URL.

Example:

```bash
cd e2e
SALEADS_URL="https://<current-saleads-environment>/login" npm run test:saleads-mi-negocio
```

## Local setup

```bash
cd e2e
npm install
npx playwright install
npm run test:saleads-mi-negocio
```

## Evidence and report

- Screenshots are attached to Playwright results for each checkpoint.
- Final report is generated as:
  - `saleads-mi-negocio-final-report.json`
- The report includes:
  - PASS/FAIL per required validation area
  - final app URL
  - captured legal URLs (terms and privacy)
