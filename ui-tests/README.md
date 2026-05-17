# SaleADS UI tests

This folder contains Playwright-based UI tests for SaleADS workflows.

## Implemented test

- `tests/saleads_mi_negocio_full_test.spec.ts`
  - Login with Google.
  - Validate full **Mi Negocio** workflow.
  - Capture screenshots on key checkpoints.
  - Validate legal links (new tab or same tab) and return to app.
  - Print + attach final PASS/FAIL report.

## Run

From this folder:

```bash
npx playwright install chromium
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:saleads:mi-negocio
```

Alternative input variable:

```bash
SALEADS_BASE_URL="https://<your-environment-base-url>" npm run test:saleads:mi-negocio
```

The test intentionally avoids hardcoded environment domains and relies on runtime environment variables.
