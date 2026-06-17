# SaleADS.ai E2E Workflows

This folder contains Playwright end-to-end tests for SaleADS.ai.

## Implemented workflow

- `tests/saleads-mi-negocio-full.spec.ts`
  - Test name: `saleads_mi_negocio_full_test`
  - Scope:
    1. Login with Google.
    2. Open **Mi Negocio** menu.
    3. Validate **Agregar Negocio** modal.
    4. Open **Administrar Negocios**.
    5. Validate **Información General**.
    6. Validate **Detalles de la Cuenta**.
    7. Validate **Tus Negocios**.
    8. Validate **Términos y Condiciones** (same tab or new tab).
    9. Validate **Política de Privacidad** (same tab or new tab).
    10. Produce PASS/FAIL JSON report by section.

## Environment variables

- `SALEADS_LOGIN_URL` (required unless `SALEADS_BASE_URL` is set): login URL for the target environment.
- `SALEADS_BASE_URL` (fallback): alternative to `SALEADS_LOGIN_URL`.
- `SALEADS_EXPECTED_USER_NAME` (optional): strict expected value for user name validation in "Información General".

The test intentionally does **not** hardcode any SaleADS domain and can run against dev/staging/prod by changing the environment variable value.

## Run

From `e2e/`:

```bash
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://<your-saleads-login-url>" npm run test:saleads-mi-negocio
```

## Evidence output

- Screenshots: `e2e/artifacts/screenshots/`
- JSON report: `e2e/artifacts/reports/saleads_mi_negocio_full_test.report.json`
- Playwright HTML report: `e2e/playwright-report/`
