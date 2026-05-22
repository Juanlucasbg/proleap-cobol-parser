# SaleADS Mi Negocio full workflow test

This package contains the Playwright E2E test `saleads_mi_negocio_full_test` for validating:

1. Login with Google.
2. Mi Negocio menu behavior.
3. Agregar Negocio modal.
4. Administrar Negocios account sections.
5. Información General validations.
6. Detalles de la Cuenta validations.
7. Tus Negocios validations.
8. Términos y Condiciones navigation (same tab or popup).
9. Política de Privacidad navigation (same tab or popup).
10. Final PASS/FAIL report with evidence.

## Environment-agnostic behavior

- No domain is hardcoded.
- If the browser already starts on the SaleADS login page, the test continues directly.
- If Playwright starts on `about:blank`, set `SALEADS_LOGIN_URL` for your environment:
  - dev, staging, or production login URL.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
# Using an environment URL
SALEADS_LOGIN_URL="https://<your-saleads-login-url>" npm test

# Or run headed for local debugging
SALEADS_LOGIN_URL="https://<your-saleads-login-url>" npm run test:headed
```

## Evidence and report

The test captures screenshots at key checkpoints and writes a JSON report:

- `saleads_mi_negocio_full_test-report.json`

Both the report and images are attached to the Playwright test output (`test-results`).
